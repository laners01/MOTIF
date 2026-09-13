package com.ssafy.motif.media.encode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 재생용 영상 산출물을 만든다 — 렌디션 + HLS + 썸네일 (ADR-0010 D1·D2·D4).
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
 * ├── 480p/index.m3u8    480p/data.m4s
 * ├── 720p/index.m3u8    720p/data.m4s
 * └── 1080p/index.m3u8   1080p/data.m4s
 * </pre>
 *
 * <p>화질 디렉터리는 <b>원본 해상도에 따라 줄어든다</b> — 원본보다 큰 단은 만들지
 * 않는다({@link #ladderFor}). 720 원본이면 1080p 디렉터리가 없다.
 *
 * <p><b>입력은 세로 영상만이다.</b> 가로가 들어오면 사다리의 의미가 뒤집히므로
 * 처리하지 않고 던진다({@link #requirePortrait}).
 */
public class HlsPackager {

    /** 초. 조각 길이가 곧 화질 전환 지연이다 — 짧을수록 빨리 반응하고 재생목록이 길어진다. */
    private static final int SEGMENT_SECONDS = 2;

    /** 프레임레이트를 고정해야 GOP 프레임 수(=fps × 2초)가 결정된다. */
    private static final int FPS = 30;

    /**
     * 썸네일 추출 지점 기본값(초). <b>첫 프레임이 아니다</b> — 페이드인·모션블러·인물
     * 프레임 밖이 흔하다.
     *
     * <p>2026-09-14 부터 어드민이 등록 화면에서 시각을 고를 수 있다
     * ({@code MediaJobRequestedEvent.thumbnailAtMs}). 이 상수는 그 값이 없을 때만 쓴다.
     */
    private static final double THUMBNAIL_AT = 1.5;

    /**
     * 썸네일 세로 픽셀(긴 변) — {@code scale=-2:1280} 이라 세로 영상에서 720×1280 이
     * 된다. 원본 해상도로 뽑으면 120KB 로 피드에 과하다.
     *
     * <p>사다리와 달리 여기는 긴 변 기준을 그대로 둔다. 피드 목록의 작은 카드라
     * 가로 720 이면 충분하고, 올리면 목록 30개에 그대로 곱해진다.
     */
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
        packageForPlayback(source, outDir, null);
    }

    /**
     * @param thumbnailAtMs 썸네일을 뽑을 시각(ms). null 이면 {@link #THUMBNAIL_AT} 기본값.
     *                      값이 영상 길이를 넘는지는 core-api 가 이미 막았다 — 여기서
     *                      다시 검사하지 않는다(길이를 알려면 ffprobe 를 한 번 더 불러야 한다)
     */
    public void packageForPlayback(Path source, Path outDir, Long thumbnailAtMs) {
        try {
            Files.createDirectories(outDir);
            List<String> variants = new ArrayList<>();
            for (Rendition r : ladderFor(source)) {
                variants.add(encodeAndPackage(source, outDir, r));
            }
            writeMaster(outDir, variants);
            thumbnail(source, outDir.resolve(THUMBNAIL), thumbnailAtMs);
        } catch (IOException e) {
            deleteQuietly(outDir);
            throw new MediaEncodingException("산출물 디렉터리를 만들지 못했다: " + outDir, e);
        } catch (RuntimeException e) {
            deleteQuietly(outDir);
            throw e;
        }
    }

    /**
     * 이 원본에 실제로 만들 단을 고른다 — <b>원본보다 큰 단은 만들지 않는다.</b>
     *
     * <p>사다리를 원본 해상도와 무관하게 통째로 돌면 <b>없는 화질을 지어낸다.</b>
     * 720×1280 원본에 {@code scale=1080:-2} 를 걸면 1080×1920 이 나오는데, 내용은
     * 720 을 늘린 흐릿한 영상이면서 마스터에는 {@code RESOLUTION=1080x1920} 이
     * 적힌다. ABR 은 그 숫자를 믿고 그것을 최고 화질로 고른다 — 대역폭만 더 쓰고
     * 화질은 더 나쁘다.
     *
     * <p><b>건너뛴 단은 실패가 아니다.</b> 현재 실패 정책은 "전부 성공하거나 아무것도
     * 남기지 않는다"(ADR-0010 D4)인데, 건너뛴 단을 실패로 처리하면 480p 원본이
     * 통째로 DLQ 로 간다. 그래서 예외를 던지지 않고 계획에서 빼는 방식으로 가른다.
     *
     * <p>판단에 어드민 입력이 필요 없다 — 영상만 보면 된다.
     */
    private List<Rendition> ladderFor(Path source) {
        Size size = sizeOf(source);
        requirePortrait(source, size);

        // 세로가 보장돼 있으므로 가로가 짧은 변이다. 사다리 값과 같은 축이다
        List<Rendition> planned = ladder.stream()
                .filter(r -> r.width() <= size.width())
                .toList();
        if (!planned.isEmpty()) {
            return planned;
        }
        // 원본이 사다리 최저단보다도 작다. 한 단도 만들지 않으면 재생할 것이 아예
        // 없으므로 원본 해상도 그대로 한 단을 만든다. 이때도 업스케일은 하지 않는다.
        Rendition lowest = ladder.get(0);
        return List.of(new Rendition(size.width() + "p", size.width(), lowest.maxrateKbps()));
    }

    /**
     * 세로 영상만 처리한다 — 피드가 세로 화면이다.
     *
     * <p>업로드 쪽 {@code VideoRules.checkChallengeOrigin} 이 이미 {@code PORTRAIT_REQUIRED}
     * 로 막지만 그것만으로는 부족하다. 그 값은 업로드 <b>화면의 JS</b> 가 읽어 폼에 실은
     * 것이라 개발자 도구로 조작할 수 있고, 무엇보다 합성 결과({@code COMPOSITE_RESULT})는
     * 업로드 검증을 아예 타지 않는다. 여기서는 실제 바이트를 ffprobe 로 읽으므로
     * 조작할 여지가 없다.
     *
     * <p>가로 영상을 그냥 통과시키면 사다리 의미가 뒤집힌다 — {@code width} 는
     * 짧은 변이라는 전제로 고른 값인데, 가로 영상에서는 긴 변이 된다. 같은
     * {@code 720p} 가 전혀 다른 화소 수를 뜻하게 되고 비트레이트 상한도 어긋난다.
     *
     * <p>여기서 던지면 정리 후 {@code dlq.job.media} 로 격리된다. 사람이 원본을 고쳐야
     * 하는 입력 오류라 재시도로 풀릴 성질이 아니다.
     */
    private static void requirePortrait(Path source, Size size) {
        if (size.height() <= size.width()) {
            throw new MediaEncodingException("세로 영상만 처리한다 — 원본이 "
                    + size.width() + "x" + size.height() + " 다: " + source);
        }
    }

    /** 원본 해상도. {@code height} 는 {@code scale=-2:height} 와 같은 축이라 바로 견줄 수 있다. */
    private Size sizeOf(Path source) {
        String resolution = ffmpeg.resolution(source);      // "1080x1920"
        try {
            int x = resolution.lastIndexOf('x');
            return new Size(Integer.parseInt(resolution.substring(0, x)),
                    Integer.parseInt(resolution.substring(x + 1)));
        } catch (RuntimeException e) {
            // 여기서 조용히 전체 사다리로 넘어가면 업스케일이 되살아난다 — 크게 실패한다
            throw new MediaEncodingException(
                    "원본 해상도를 읽지 못했다: " + source + " (" + resolution + ")", e);
        }
    }

    private record Size(int width, int height) {
    }

    /** 렌디션 하나를 인코딩하고 HLS 로 싼다. 마스터 재생목록에 넣을 두 줄을 돌려준다. */
    private String encodeAndPackage(Path source, Path outDir, Rendition r) {
        Path mp4 = outDir.resolve(r.tag() + ".tmp.mp4");
        Path dir = outDir.resolve(r.tag());

        ffmpeg.run(List.of(
                "-v", "error", "-y", "-i", source.toString(),
                "-vf", "scale=" + r.width() + ":-2,fps=" + FPS,
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

    private void thumbnail(Path source, Path out, Long atMs) {
        double at = atMs == null ? THUMBNAIL_AT : atMs / 1000.0;
        // -ss 를 -i 앞에 둔다 — 디코딩 없이 건너뛰어 훨씬 빠르다
        ffmpeg.run(List.of(
                "-v", "error", "-y", "-ss", String.valueOf(at), "-i", source.toString(),
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
