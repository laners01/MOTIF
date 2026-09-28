package com.ssafy.motif.event.contract;

import java.time.Instant;

/**
 * 가공 완료 — app-media → {@code motif.topic}(rk {@code composite.packaged}) → core-api.
 *
 * <p><b>app-media 가 {@code composite_jobs} 를 직접 쓰지 않는 이유가 이 이벤트다</b>
 * (작업명세 §2 원칙 1). 그 테이블의 쓰기 주체는 core-api 하나고, app-media 는
 * "가공을 마쳤고 산출물은 여기 있다" 만 보고한다. 예전에는 app-media 가 직접
 * UPDATE 했는데 {@code PROCESSING} 행에 결과 키를 쓰는 모양이라
 * {@code ck_composite_jobs_status_shape} 에 걸려 <b>한 번도 성공할 수 없었다</b> —
 * 재시도 3회 뒤 {@code dlq.job.media} 로 갔다(§7 함정 4).
 *
 * <p>상태를 {@code DONE} 으로 확정하고 MOTIF 결과 행을 만드는 것은 수신측 core 다.
 *
 * <p>직렬화 규약(ADR-0005, README.md): snake_case / ISO-8601 UTC / additive-only.
 * Jackson 애너테이션 금지 — 네이밍은 각 앱의 MQ 컨버터 ObjectMapper가 전담한다.
 *
 * <p><b>키를 보내고 URL 을 보내지 않는다</b>(Claim-Check) — {@link ChallengeCreatedEvent}
 * 와 같은 이유다.
 *
 * @param mediaJobUuid  후처리 잡 uuid — {@code MediaJobRequestedEvent} 의 것을 그대로 되돌린다
 * @param sourceType    {@code COMPOSITE_RESULT} · {@code CHALLENGE_ORIGIN} ·
 *                      {@code ASSET_PREVIEW}. core 는 {@code COMPOSITE_RESULT} 만 처리하고
 *                      나머지는 ack 한다 — 그쪽은 app-media 가 직접 갱신한다
 * @param sourceSeq     {@code COMPOSITE_RESULT} 면 {@code composite_jobs.job_seq}
 * @param outputPrefix  산출물 프리픽스 — {@code masterKey}·{@code thumbnailKey} 의 부모
 * @param masterKey     {@code {outputPrefix}/master.m3u8}
 * @param thumbnailKey  {@code {outputPrefix}/thumb.jpg}
 * @param durationMs    <b>원본에서 잰 길이</b>. MOTIF 결과 행({@code motif_results})의
 *                      {@code duration_ms} 가 된다 — 잡 행에는 길이 칸이 없다
 */
public record CompositePackagedEvent(
        String eventId,
        Instant occurredAt,
        String mediaJobUuid,
        String sourceType,
        Long sourceSeq,
        String outputPrefix,
        String masterKey,
        String thumbnailKey,
        Integer durationMs
) {
}
