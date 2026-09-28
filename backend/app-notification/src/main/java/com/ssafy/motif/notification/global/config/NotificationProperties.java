package com.ssafy.motif.notification.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 알림 서비스 설정 (`motif.notification`).
 *
 * @param fcmCredentialsPath 서비스 계정 JSON 경로. k8s Secret 을 파일로 마운트한다.
 *                           <b>fallback 을 두지 않는다</b>(ADR-0004) — 비어 있으면 기동에
 *                           실패해야 한다. 조용히 뜨면 발송이 전부 실패하는데 그 사실이
 *                           로그 한 줄로만 남는다
 * @param dedupTtl           멱등 키 TTL. 어드민 명세 §4 가 24h 로 고정했다 — 브로커
 *                           재전달 창보다 훨씬 길어야 중복 푸시를 막는다
 */
@ConfigurationProperties(prefix = "motif.notification")
public record NotificationProperties(String fcmCredentialsPath, Duration dedupTtl) {
}
