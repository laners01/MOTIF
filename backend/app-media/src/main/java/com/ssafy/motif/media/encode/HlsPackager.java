package com.ssafy.motif.media.encode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 재생용 영상 산출물을 만든다 — 렌디션 2종 + HLS + 썸네일 (ADR-0010 D1·D2·D4).
 *
 * <p><b>여기 파라미터는 취향이 아니라 전제조건이다.</b> GOP 가 화질 간에 어긋나면
 * 에러 없이 <b>ABR 전환만 안 된다</b> — 조각 경계는 키프레임에만 놓이기 때문이다.
 * 발견이 늦는 종류의 고장이라 {@code HlsPackagerTest} 가 키프레임 일치를 검사한다.
 *
 * <p>합성 결과와 어드민이 올린 챌린지 원본이 <b>같은 이 컴포넌트</b>를 탄다.
 * 파라미터를 두 경로에 복사하면 한쪽만 고쳤을 때 조용히 깨진다 (ADR-0010 D1).
 *
 * <p>산출물 배치 — DB 에는 {@code master.m3u8} 경로 하나만 저장한다. 나머지는
 * 같은 디렉터리 규약으로 찾으므로 스키마 변경이 없다.
 *
 * <pre>
 * &lt;outDir&gt;/
 * ├── master.m3u8
 * ├── thumb.jpg
 * ├── 480/index.m3u8   480/data.m4s
 * └── 720/index.m3u8   720/data.m4s
 * </pre>
 */
public class HlsPackager {

    /** 초. 조각 길이가 곧 화질 전환 지연이다 — 짧을수록 빨리 반응하고 재생목록이 길어진다. */
    private static final int SEGMENT_SECONDS = 2;

    /** 프레임레이트를 고정해야 GOP 프레임 수(=fps × 2초)가 결정된다. */
    private static final int FPS = 30;

    /** 썸네일 추출 지점(초). <b>첫 프레임이 아니다</b> — 페이드인·모션블러·인물 프레임 밖이 흔하다. */
    private static final double THUMBNAIL_AT = 1.5;

    /** 썸네일 짧은 변. 원본 해상도로 뽑으면 120KB 로 피드에 과하다. */
    private static final int THUMBNAIL_HEIGHT = 1280;

    public static final String MASTER = "master.m3u8";
    public static final String THUMBNAIL = "thumb.jpg";

    private final Ffmpeg ffmpeg;
    private final List<Rendition> ladder;

    public HlsPackager() {
        this("ffmpeg", "ffprobe", Rendition.LADDER);
    }

    public HlsPackager(String ffmpegBin, String ffprobeBin, List<Rendition> ladder) {
        this.ffmpeg = new Ffmpeg(ffmpegBin, ffprobeBin);
        this.ladder = List.copyOf(ladder);
    }

    /**
     * {@code source} 를 재생용 산출물로 가공해 {@code outDir} 에 쓴다.
     *
     * <p>실패하면 {@link MediaEncodingException} 을 던지고 <b>부분 산출물을 지운다</b>.
     * 렌디션 하나만 남으면 ABR 이 한 단계로 떨어져 조용히 열화되므로, 전부 성공하거나
     * 아무것도 남기지 않는다 (ADR-0010 D4 실패 처리).
     */
    public void packageForPlayback(Path source, Path outDir) {
        try {
            Files.createDirectories(outDir);
            List<String> variants = new ArrayList<>();
            for (Rendition r : ladder) {
                variants.add(encodeAndPackage(source, outDir, r));
            }
            writeMaster(outDir, variants);
            thumbnail(source, outDir.resolve(THUMBNAIL));
        } catch (IOException e) {
            deleteQuietly(outDir);
            throw new MediaEncodingException("산출물 디렉터리를 만들지 못했다: " + outDir, e);
        } catch (RuntimeException e) {
            deleteQuietly(outDir);
            throw e;
        }
    }

    /** 렌디션 하나를 인코딩하고 HLS 로 싼다. 마스터 재생목록에 넣을 두 줄을 돌려준다. */
    private String encodeAndPackage(Path source, Path outDir, Rendition r) {
        Path mp4 = outDir.resolve(r.tag() + ".tmp.mp4");
        Path dir = outDir.resolve(r.tag());

        ffmpeg.run(List.of(
                "-v", "error", "-y", "-i", source.toString(),
                "-vf", "scale=-2:" + r.height() + ",fps=" + FPS,
                "-c:v", "libx264", "-preset", "veryfast", "-crf", "26",
                "-maxrate", r.maxrateKbps() + "k", "-bufsize", r.bufsizeKbps() + "k",
                // 2초마다 강제 키프레임. sc_threshold=0 이 장면 전환 감지로 인한
                // 임의 삽입을 막는다 — 이게 없으면 화질 간 경계가 어긋난다
                "-g", String.valueOf(FPS * SEGMENT_SECONDS),
                "-keyint_min", String.valueOf(FPS * SEGMENT_SECONDS),
                "-sc_threshold", "0",
                "-c:a", "aac", "-b:a", "96k",
                "-movflags", "+faststart",
                mp4.toString()));

        long bandwidth = ffmpeg.bitrate(mp4);
        String resolution = ffmpeg.resolution(mp4);

        try {
            Files.createDirectories(dir);
            // -c copy — 패키징에 재인코딩이 필요 없다. single_file 이 조각을 한 파일 안의
            // 바이트 구간으로 지정하므로 파일이 폭증하지 않는다(화질당 2개)
            ffmpeg.run(List.of(
                    "-v", "error", "-y", "-i", mp4.toString(), "-c", "copy",
                    "-f", "hls", "-hls_time", String.valueOf(SEGMENT_SECONDS),
                    "-hls_playlist_type", "vod",
                    "-hls_segment_type", "fmp4", "-hls_flags", "single_file",
                    "-hls_fmp4_init_filename", "init.mp4",
                    "-hls_segment_filename", dir.resolve("data.m4s").toString(),
                    dir.resolve("index.m3u8").toString()));
            Files.deleteIfExists(mp4);      // 중간 산출물 — 서빙하지 않는다
        } catch (IOException e) {
            throw new MediaEncodingException("패키징 중 파일 오류: " + dir, e);
        }

        return "#EXT-X-STREAM-INF:BANDWIDTH=" + bandwidth + ",RESOLUTION=" + resolution
                + "\n" + r.tag() + "/index.m3u8";
    }

    private void writeMaster(Path outDir, List<String> variants) {
        String body = "#EXTM3U\n#EXT-X-VERSION:7\n" + String.join("\n", variants) + "\n";
        try {
            Files.writeString(outDir.resolve(MASTER), body, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new MediaEncodingException("마스터 재생목록을 쓰지 못했다", e);
        }
    }

    private void thumbnail(Path source, Path out) {
        // -ss 를 -i 앞에 둔다 — 디코딩 없이 건너뛰어 훨씬 빠르다
        ffmpeg.run(List.of(
                "-v", "error", "-y", "-ss", String.valueOf(THUMBNAIL_AT), "-i", source.toString(),
                "-frames:v", "1", "-vf", "scale=-2:" + THUMBNAIL_HEIGHT, "-q:v", "5",
                out.toString()));
    }

    private static void deleteQuietly(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // 정리 실패가 원래 예외를 덮지 않게 한다
                }
            });
        } catch (IOException ignored) {
            // 같음
        }
    }
}
