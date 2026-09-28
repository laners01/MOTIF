package com.ssafy.motif.media.encode;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * ffmpeg·ffprobe 호출 한 곳.
 *
 * <p>바이트를 JVM 힙으로 올리지 않는다 — 파일 경로만 넘기고 프로세스가 직접 읽고 쓴다.
 * 60초 1080p 영상이 25MB 인데 동시 처리분까지 힙에 얹을 이유가 없다.
 */
final class Ffmpeg {

    /** 인코딩은 영상 길이의 0.1배속이다(실측). 60초 영상 6초 — 넉넉히 잡아도 5분이면 이상 상황이다. */
    private static final long TIMEOUT_MINUTES = 5;

    private final String ffmpeg;
    private final String ffprobe;

    Ffmpeg(String ffmpeg, String ffprobe) {
        this.ffmpeg = ffmpeg;
        this.ffprobe = ffprobe;
    }

    void run(List<String> args) {
        exec(prepend(ffmpeg, args));
    }

    /** ffprobe 출력 첫 줄. 값이 없으면 빈 문자열. */
    String probe(List<String> args) {
        String out = exec(prepend(ffprobe, args));
        int nl = out.indexOf('\n');
        return (nl < 0 ? out : out.substring(0, nl)).trim();
    }

    private static List<String> prepend(String bin, List<String> args) {
        List<String> cmd = new ArrayList<>(args.size() + 1);
        cmd.add(bin);
        cmd.addAll(args);
        return cmd;
    }

    private String exec(List<String> cmd) {
        Process p = null;
        try {
            p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            // 파이프 버퍼가 차면 자식이 블로킹된다 — waitFor 보다 먼저 읽는다
            String out = new String(p.getInputStream().readAllBytes());
            if (!p.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                throw new MediaEncodingException("시간 초과(" + TIMEOUT_MINUTES + "분): " + cmd.get(0));
            }
            if (p.exitValue() != 0) {
                throw new MediaEncodingException(
                        "종료 코드 " + p.exitValue() + " — " + String.join(" ", cmd) + "\n" + out);
            }
            return out;
        } catch (IOException e) {
            throw new MediaEncodingException("실행 실패: " + String.join(" ", cmd), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MediaEncodingException("인터럽트: " + String.join(" ", cmd), e);
        } finally {
            if (p != null && p.isAlive()) {
                p.destroyForcibly();
            }
        }
    }

    /** 평균 비트레이트(bps). 마스터 재생목록의 {@code BANDWIDTH} 값이 된다. */
    long bitrate(Path file) {
        String v = probe(List.of("-v", "error", "-show_entries", "format=bit_rate",
                "-of", "default=nw=1:nk=1", file.toString()));
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            throw new MediaEncodingException("비트레이트를 읽지 못했다: " + file + " (" + v + ")", e);
        }
    }

    /**
     * 길이(ms) — {@code motif_results.duration_ms} 의 출처다 (S15P21D104-249).
     *
     * <p><b>산출물이 아니라 원본에서 잰다.</b> HLS 는 조각이 흩어져 있어 한 번에 재려면
     * 재생목록을 파싱해야 하고, 값도 세그먼트 경계로 반올림된다.
     *
     * <p>길이를 못 읽으면 {@code null} 이다 — 던지지 않는다. 가공 자체는 이미 끝났는데
     * 부가 정보 하나 때문에 잡 전체를 DLQ 로 보낼 이유가 없다. 받는 쪽이 에셋 길이로
     * 갈음한다(작업명세 §4.4).
     */
    Integer duration(Path file) {
        String v = probe(List.of("-v", "error", "-show_entries", "format=duration",
                "-of", "default=nw=1:nk=1", file.toString()));
        try {
            return (int) Math.round(Double.parseDouble(v) * 1000);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** {@code 406x720} 형태. */
    String resolution(Path file) {
        return probe(List.of("-v", "error", "-select_streams", "v:0",
                "-show_entries", "stream=width,height", "-of", "csv=p=0:s=x", file.toString()));
    }
}
