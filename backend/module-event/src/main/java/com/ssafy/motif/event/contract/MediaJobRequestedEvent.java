package com.ssafy.motif.event.contract;

import java.time.Instant;

/**
 * 미디어 후처리 요청 — core-api → {@code motif.topic}(rk {@code job.media}) → app-media.
 *
 * <p>합성 워커 결과와 어드민이 올린 챌린지 원본이 <b>같은 이 이벤트</b>로 들어온다.
 * 인코딩 파라미터를 한 곳에서만 관리하기 위한 구조다 (ADR-0010 D1).
 *
 * <p>직렬화 규약(ADR-0005, README.md): snake_case / ISO-8601 UTC / additive-only.
 * Jackson 애너테이션 금지 — 네이밍은 각 앱의 MQ 컨버터 ObjectMapper가 전담한다.
 *
 * @param eventId      이벤트 자체의 uuid (중복 전달 방어용)
 * @param occurredAt   발생 시각 (ISO-8601 UTC)
 * @param mediaJobUuid 이 후처리 잡의 uuid — 전 구간 멱등 키
 * @param sourceType   {@code COMPOSITE_RESULT}(합성 결과) | {@code CHALLENGE_ORIGIN}(어드민 업로드)
 * @param sourceSeq    갱신할 행의 PK — sourceType 에 따라 composite_jobs 또는 challenges
 * @param sourceKey    S3 원본 키. 재생용이 아니라 <b>입력</b>이므로 MP4 그대로다
 * @param outputPrefix S3 산출물 프리픽스. 이 아래에 master.m3u8·thumb.jpg·화질별 디렉터리가 깔린다
 */
public record MediaJobRequestedEvent(
        String eventId,
        Instant occurredAt,
        String mediaJobUuid,
        String sourceType,
        Long sourceSeq,
        String sourceKey,
        String outputPrefix
) {
    public static final String SOURCE_COMPOSITE_RESULT = "COMPOSITE_RESULT";
    public static final String SOURCE_CHALLENGE_ORIGIN = "CHALLENGE_ORIGIN";
}
