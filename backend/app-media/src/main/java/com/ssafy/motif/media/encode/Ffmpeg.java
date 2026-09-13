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

    /** {@code 406x720} 형태. */
    String resolution(Path file) {
        return probe(List.of("-v", "error", "-select_streams", "v:0",
                "-show_entries", "stream=width,height", "-of", "csv=p=0:s=x", file.toString()));
    }
}
