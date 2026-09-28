package com.ssafy.motif.event.contract;

import java.time.Instant;

/**
 * 합성 완료 — GPU 워커 → {@code motif.topic}(rk {@code composite.done})
 * → evt.core, evt.notification.
 *
 * <p>직렬화 규약(ADR-0005, README.md): snake_case / ISO-8601 UTC / additive-only.
 * Jackson 애너테이션 금지 — 네이밍은 각 앱의 MQ 컨버터 ObjectMapper가 전담한다.
 *
 * @param eventId    이벤트 자체의 uuid (중복 전달 방어용)
 * @param occurredAt 발생 시각 (ISO-8601 UTC)
 * @param jobUuid    완료된 잡의 uuid — 전 구간 멱등 키
 */
public record CompositeJobDoneEvent(
        String eventId,
        Instant occurredAt,
        String jobUuid
) {
}
