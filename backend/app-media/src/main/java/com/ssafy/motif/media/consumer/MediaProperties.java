package com.ssafy.motif.media.consumer;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param ffmpeg  ffmpeg 실행 경로
 * @param ffprobe ffprobe 실행 경로
 * @param workDir 작업 디렉터리. 잡마다 하위 폴더를 만들고 끝나면 지운다
 */
@ConfigurationProperties(prefix = "motif.media")
public record MediaProperties(String ffmpeg, String ffprobe, String workDir) {
}
