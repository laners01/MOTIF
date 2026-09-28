package com.ssafy.motif.event.contract;

import java.time.Instant;

/**
 * 합성 잡 요청 — core-api → {@code motif.topic}(rk {@code job.composite}) → GPU 워커.
 *
 * <p>직렬화 규약(ADR-0005, README.md): snake_case / ISO-8601 UTC / additive-only.
 * Jackson 애너테이션 금지 — 네이밍은 각 앱의 MQ 컨버터 ObjectMapper가 전담한다.
 *
 * @param eventId    이벤트 자체의 uuid (중복 전달 방어용)
 * @param occurredAt 발생 시각 (ISO-8601 UTC)
 * @param jobUuid    composite_jobs 행의 uuid — 전 구간 멱등 키
 * @param jobType    MOTIF(유료, priority 5) | CHALLENGE(무료, priority 0)
 */
public record CompositeJobRequestedEvent(
        String eventId,
        Instant occurredAt,
        String jobUuid,
        String jobType
) {
}
