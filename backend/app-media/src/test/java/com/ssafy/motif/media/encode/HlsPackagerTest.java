package com.ssafy.motif.media.encode;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 산출물이 ABR 로 재생 가능한 형태인지 검사한다.
 *
 * <p>가장 중요한 것은 {@code 화질_간_키프레임이_정확히_일치한다} 다. 이게 깨지면
 * 예외도 로그도 없이 <b>화질 전환만 안 된다</b> — 사람이 눈으로 보기 전엔 모른다.
 *
 * <p>고정 영상 파일을 두지 않는다. ffmpeg 합성 소스로 만들어 쓰면 레포가 가벼워지고
 * 어느 환경에서도 같은 입력이 된다.
 */
class HlsPackagerTest {

    private static final int DURATION_SECONDS = 8;

    @TempDir
    Path tmp;

    @BeforeAll
    static void ffmpegAvailable() {
        assumeTrue(hasBinary("ffmpeg") && hasBinary("ffprobe"),
                "ffmpeg/ffprobe 가 없어 건너뛴다");
    }

    @Test
    void 화질_간_키프레임이_정확히_일치한다() {
        Path out = pack();

        List<String> a = segmentBoundaries(out, "480p");
        List<String> b = segmentBoundaries(out, "720p");

        // 조각 경계는 키프레임에만 놓인다. 두 화질의 키프레임 시각이 다르면
        // 720p 3번 조각 다음에 480p 4번 조각을 붙일 수 없다 = 전환 불가
        assertThat(a).isEqualTo(b);
        assertThat(a).hasSizeGreaterThan(1);
    }

    @Test
    void 조각이_한_파일_안의_바이트_구간으로_지정된다() throws IOException {
        Path out = pack();

        for (String tag : List.of("480p", "720p")) {
            Path dir = out.resolve(tag);
            try (Stream<Path> files = Files.list(dir)) {
                // data.m4s + index.m3u8 두 개뿐 — 조각마다 파일이 생기면 여기서 걸린다
                assertThat(files).hasSize(2);
            }
            String playlist = Files.readString(dir.resolve("index.m3u8"));
            assertThat(playlist).contains("#EXT-X-BYTERANGE:");
            assertThat(playlist).contains("#EXT-X-MAP:URI=\"data.m4s\"");
        }
    }

    @Test
    void 마스터_재생목록이_화질별_대역폭을_담는다() throws IOException {
        Path out = pack();

        String master = Files.readString(out.resolve(HlsPackager.MASTER));
        assertThat(master).startsWith("#EXTM3U");
        assertThat(master).contains("480p/index.m3u8", "720p/index.m3u8");

        List<Long> bandwidths = master.lines()
                .filter(l -> l.startsWith("#EXT-X-STREAM-INF:"))
                .map(l -> Long.parseLong(l.replaceAll(".*BANDWIDTH=(\\d+).*", "$1")))
                .toList();
        assertThat(bandwidths).hasSize(2);
        // ABR 이 이 값으로 화질을 고른다. 낮은 화질이 더 커지면 사다리가 뒤집힌다
        assertThat(bandwidths.get(0)).isLessThan(bandwidths.get(1));
    }

    @Test
    void 비트레이트가_상한_안에_든다() {
        Path out = pack();

        for (Rendition r : Rendition.LADDER) {
            long bps = bitrateOf(out.resolve(HlsPackager.MASTER), r.tag());
            // 상한이 없으면 내용에 따라 원본 대비 15~69% 로 4.6배 편차가 난다
            assertThat(bps).as(r.tag() + " 비트레이트")
                    .isLessThanOrEqualTo(r.maxrateKbps() * 1000L);
        }
    }

    @Test
    void 썸네일이_첫_프레임이_아니라_1_5초_지점에서_나온다() throws IOException {
        Path out = pack();

        Path thumb = out.resolve(HlsPackager.THUMBNAIL);
        assertThat(thumb).exists();
        // 원본 해상도로 뽑으면 120KB 가 넘는다 — 피드 목록 30개면 3.6MB 다
        assertThat(Files.size(thumb)).isBetween(5_000L, 80_000L);
    }

    @Test
    void 실패하면_부분_산출물을_남기지_않는다() {
        Path out = tmp.resolve("fail");
        Path missing = tmp.resolve("없는파일.mp4");

        try {
            new HlsPackager().packageForPlayback(missing, out);
        } catch (MediaEncodingException expected) {
            // 렌디션 하나만 남으면 ABR 이 한 단계로 떨어져 조용히 열화된다
            assertThat(out).doesNotExist();
            return;
        }
        throw new AssertionError("예외가 나야 한다");
    }

    // ── 도우미 ────────────────────────────────────────────────────

    private Path pack() {
        Path src = tmp.resolve("src.mp4");
        if (!Files.exists(src)) {
            run("ffmpeg", "-v", "error", "-y",
                    "-f", "lavfi", "-i", "testsrc2=size=1080x1920:rate=30:duration=" + DURATION_SECONDS,
                    "-f", "lavfi", "-i", "sine=frequency=440:duration=" + DURATION_SECONDS,
                    "-c:v", "libx264", "-preset", "ultrafast", "-b:v", "3000k", "-pix_fmt", "yuv420p",
                    "-c:a", "aac", "-shortest", src.toString());
        }
        Path out = tmp.resolve("out");
        if (!Files.exists(out.resolve(HlsPackager.MASTER))) {
            new HlsPackager().packageForPlayback(src, out);
        }
        return out;
    }

    /** 재생목록의 {@code #EXTINF} 누적값 = 조각 경계 = 키프레임 시각. */
    private List<String> segmentBoundaries(Path out, String tag) {
        try {
            double t = 0;
            List<String> times = new java.util.ArrayList<>();
            for (String line : Files.readAllLines(out.resolve(tag).resolve("index.m3u8"))) {
                if (line.startsWith("#EXTINF:")) {
                    times.add(String.format("%.3f", t));
                    t += Double.parseDouble(line.substring(8).replace(",", ""));
                }
            }
            return times;
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    /** 마스터 재생목록에서 {@code <tag>/index.m3u8} 바로 위 줄의 BANDWIDTH. */
    private long bitrateOf(Path master, String tag) {
        try {
            List<String> lines = Files.readAllLines(master);
            for (int i = 0; i < lines.size() - 1; i++) {
                if (lines.get(i).startsWith("#EXT-X-STREAM-INF:")
                        && lines.get(i + 1).startsWith(tag + "/")) {
                    return Long.parseLong(lines.get(i).replaceAll(".*BANDWIDTH=(\\d+).*", "$1"));
                }
            }
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        throw new AssertionError(tag + " 를 마스터 재생목록에서 찾지 못했다");
    }

    private static boolean hasBinary(String name) {
        try {
            return new ProcessBuilder(name, "-version")
                    .redirectErrorStream(true).start().waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static void run(String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes());
            if (p.waitFor() != 0) {
                throw new AssertionError(String.join(" ", cmd) + "\n" + out);
            }
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
