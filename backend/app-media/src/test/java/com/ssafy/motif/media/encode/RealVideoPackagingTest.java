package com.ssafy.motif.media.encode;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 실제 영상으로 돌려보는 검사 — 합성 클립이 아니라 팀이 준 원본을 쓴다.
 *
 * <p>{@code HlsPackagerTest} 는 합성 소스라 비트레이트·GOP 가 인위적이다. 여기서는
 * 실제 1080p 영상의 특성(가변 비트레이트, 제각각인 원본 GOP)에서도 사다리가
 * 예측 가능하게 나오는지 본다.
 *
 * <p>기본은 건너뛴다. 영상을 지정해야 돈다:
 *
 * <pre>
 * ./gradlew :app-media:test --tests '*RealVideoPackagingTest' \
 *     -Dmotif.test.video=/path/to/video.mp4 -Dmotif.test.out=/tmp/hls
 * </pre>
 */
class RealVideoPackagingTest {

    private static final String VIDEO_PROP = "motif.test.video";
    private static final String OUT_PROP = "motif.test.out";

    @Test
    void 실제_영상에서도_사다리가_예측_가능하게_나온다() throws IOException {
        String video = System.getProperty(VIDEO_PROP);
        assumeTrue(video != null, "-D" + VIDEO_PROP + " 이 없어 건너뛴다");

        Path source = Path.of(video);
        assertThat(source).exists();
        Path out = Path.of(System.getProperty(OUT_PROP, "build/real-hls"));

        long started = System.currentTimeMillis();
        new HlsPackager().packageForPlayback(source, out);
        long elapsedMs = System.currentTimeMillis() - started;

        List<String> master = Files.readAllLines(out.resolve(HlsPackager.MASTER));
        System.out.println("=== 마스터 재생목록 ===");
        master.forEach(System.out::println);

        long total = 0;
        try (var walk = Files.walk(out)) {
            total = walk.filter(Files::isRegularFile).mapToLong(f -> {
                try {
                    return Files.size(f);
                } catch (IOException e) {
                    return 0;
                }
            }).sum();
        }
        long sourceSize = Files.size(source);
        System.out.printf("원본 %.1fMB → 산출물 %.1fMB (%.0f%%), %.1f초%n",
                sourceSize / 1e6, total / 1e6, total * 100.0 / sourceSize, elapsedMs / 1000.0);

        // 화질별 조각 경계가 일치해야 전환이 된다 — 실제 영상에서도
        assertThat(boundaries(out, "480p")).isEqualTo(boundaries(out, "720p"));

        for (Rendition r : Rendition.LADDER) {
            try (var files = Files.list(out.resolve(r.tag()))) {
                assertThat(files).as(r.tag() + " 산출 파일").hasSize(2);
            }
        }
        assertThat(out.resolve(HlsPackager.THUMBNAIL)).exists();
    }

    private List<String> boundaries(Path out, String tag) throws IOException {
        double t = 0;
        List<String> times = new java.util.ArrayList<>();
        for (String line : Files.readAllLines(out.resolve(tag).resolve("index.m3u8"))) {
            if (line.startsWith("#EXTINF:")) {
                times.add(String.format("%.3f", t));
                t += Double.parseDouble(line.substring(8).replace(",", ""));
            }
        }
        return times;
    }
}
