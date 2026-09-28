package com.ssafy.motif.event.contract;

import java.time.Instant;

/**
 * 합성 종료(성공·실패 확정) — core-api → {@code motif.topic}(rk {@code composite.finished})
 * → app-notification (S15P21D104-318).
 *
 * <p><b>왜 core 가 내는가.</b> 알림 서비스는 {@code user_seq} 를 모르고(core 스키마에
 * 접근하지 않는다), AI 콜백 시점에는 아직 {@code DONE} 이 아니다 — 가공(app-media)이
 * 남아 있다. 확정할 수 있는 곳이 core 뿐이라 여기서 낸다.
 *
 * <p><b>푸시는 확정 뒤에만 나간다</b>(작업명세 §2 원칙 3). 앱은 폴링하지 않으므로
 * (§1.6) 푸시가 먼저 가면 사용자가 §13.3 에서 {@code PROCESSING} 을 본다.
 *
 * <p>{@code composite.*} 바인딩이라 <b>core 자신도 {@code evt.core} 로 이 키를 받는다</b> —
 * 브로커 바인딩을 바꾸면 재기동이라 그대로 두고, core 리스너가 ack 만 하고 버린다.
 *
 * <p>직렬화 규약(ADR-0005, README.md): snake_case / ISO-8601 UTC / additive-only.
 *
 * @param jobUuid   FCM 페이로드에 그대로 실리는 식별자 (§14.3). 앱은 이 값으로 §13.3 을 1회 부른다
 * @param jobType   {@code CHALLENGE} | {@code MOTIF} — 푸시 문구가 갈린다
 * @param userSeq   수신자. 그 사람의 활성 기기 전부에 보낸다
 * @param status    {@code DONE} | {@code FAILED}
 * @param failCode  실패 사유. {@code DONE} 이면 {@code null}
 * @param resultSeq MOTIF 성공이면 {@code motif_results.result_seq}, 그 외 {@code null}
 */
public record CompositeFinishedEvent(
        String eventId,
        Instant occurredAt,
        String jobUuid,
        String jobType,
        Integer userSeq,
        String status,
        String failCode,
        Long resultSeq
) {

    public static final String STATUS_DONE = "DONE";
    public static final String STATUS_FAILED = "FAILED";
}
