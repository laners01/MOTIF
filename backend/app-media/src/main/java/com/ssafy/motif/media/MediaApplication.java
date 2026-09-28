package com.ssafy.motif.media;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 미디어 후처리 서비스 — {@code job.media} 를 소비해 재생용 산출물을 만든다.
 *
 * <p>합성 워커(GPU)가 낸 결과와 어드민이 올린 챌린지 원본이 <b>같은 경로</b>로 들어온다.
 * 인코딩 파라미터를 한 곳에서만 관리하기 위한 구조다 (ADR-0010 D1).
 */
@SpringBootApplication
public class MediaApplication {

    public static void main(String[] args) {
        SpringApplication.run(MediaApplication.class, args);
    }
}
