package com.ssafy.motif.event.contract;

import java.time.Instant;

/**
 * 회원 정지 — core-api → {@code motif.topic}(rk {@code user.suspended}) → app-notification.
 *
 * <p>컨슈머는 이 회원의 {@code device_tokens.is_active} 를 내려 더 이상 FCM 이 가지 않게
 * 한다(어드민 명세 §3.14.3·§4). <b>해제 이벤트는 없다</b> — 재로그인 시 토큰이 다시
 * 등록되며 자연 복구된다.
 *
 * <p>정지 자체는 이 메시지와 무관하게 이미 끝나 있다. refresh 토큰을 전부 폐기했으므로
 * 재진입은 막혀 있고, 이 이벤트는 "이미 손에 있는 기기 토큰으로 푸시가 계속 가는 것"만
 * 막는다. 그래서 발행 실패로 정지를 되돌리지 않는다.
 *
 * <p>직렬화 규약(ADR-0005): snake_case / ISO-8601 UTC / additive-only.
 * Jackson 애너테이션 금지 — 네이밍은 각 앱의 MQ 컨버터 ObjectMapper 가 전담한다.
 *
 * @param eventId    이벤트 uuid — 컨슈머 멱등 키({@code SETNX evt:{eventId}} TTL 24h)
 * @param occurredAt 발생 시각 (ISO-8601 UTC)
 * @param userSeq    정지된 회원
 */
public record UserSuspendedEvent(
        String eventId,
        Instant occurredAt,
        Integer userSeq
) {
}
