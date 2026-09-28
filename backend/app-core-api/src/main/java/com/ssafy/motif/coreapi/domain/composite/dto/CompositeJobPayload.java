package com.ssafy.motif.coreapi.domain.composite.dto;

import tools.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import tools.jackson.databind.annotation.JsonNaming;

import java.util.UUID;

/**
 * AI 접수 본문 (AI 서버 명세 §3.2 · 작업명세 §3.1) — core-api → AI.
 *
 * <p><b>snake_case 다.</b> REST 응답은 camelCase 가 규약이지만(§1.3), AI 가 만지는 JSON 은
 * 전부 snake_case 로 통일한다 — Python 쪽에 표기법이 둘이면 반드시 한 번 틀린다.
 * {@link JsonNaming} 한 줄로 끝난다(Jackson 3 {@code tools.jackson}).
 *
 * <p><b>이 레코드의 필드 이름·개수가 곧 AI 와의 계약이다.</b> 필드를 더하거나 빼면
 * AI 서버 명세 §4 를 먼저 고친다 — {@code CompositeJobPayloadsTest} 가 명세의 두 JSON
 * 과 필드 단위로 대조하므로, 명세를 안 고치고 여기만 고치면 테스트가 막는다.
 *
 * <p>조립은 {@code CompositeJobPayloads} 하나다 — §13.2·§13.4·스윕이 같은 잡 행에서
 * 같은 페이로드를 만들어야 한다.
 *
 * @param baseVideoKey   합성 베이스. <b>항상 MP4</b> — {@code MediaKeys.sourceMp4Of} 가
 *                       보장한다. {@code master.m3u8} 를 보내면 AI 가 재생목록을 연다
 * @param slot           교체할 슬롯 <b>하나</b>. MOTIF 면 {@code null} 이다 — 교체할
 *                       인물이라는 개념 자체가 챌린지의 것이다
 * @param baseDurationMs 베이스 길이(ms) — 결과 길이의 기준
 */
@JsonNaming(SnakeCaseStrategy.class)
public record CompositeJobPayload(
        UUID jobUuid,
        String jobType,
        short attempt,
        String rawVideoKey,
        String baseVideoKey,
        String outputVideoKey,
        boolean correctionEnabled,
        Slot slot,
        int baseDurationMs) {

    /**
     * 교체할 슬롯.
     *
     * <p><b>배열이 아니라 하나다</b>(2026-09-22). 사용자가 고르는 것은 함께할 아티스트
     * 한 명이고 편집본도 슬롯 1개당 1개라, 한 잡이 교체하는 자리는 언제나 하나다.
     * 조합 편집본이 있던 시절의 흔적으로 {@code slots[]} 배열과 바깥 합산 마스크가
     * 남아 있었는데, 둘 다 슬롯 하나에서 유도되는 값이라 걷어냈다.
     *
     * @param participantMask <b>단일 비트</b>(1·2·4·8) = {@code 1 << slotOrder}.
     *                        합성 자체에는 쓰이지 않는다 — 누구를 바꿀지는 베이스 영상
     *                        (그 사람이 이미 지워져 있다)과 {@code anchor} 가 말한다.
     *                        AI 로그에서 어느 자리 잡인지 가리는 식별자로 남긴다
     * @param anchor          촬영 전 선택 화면에서 그 슬롯의 프로필 원이 앉는 자리.
     *                        AI 에는 <b>위치 힌트</b>이고 픽셀 bbox 는 없다(S15P21D104-618).
     *                        0017 이전 챌린지 행은 {@code null} 이다
     */
    @JsonNaming(SnakeCaseStrategy.class)
    public record Slot(int participantMask, Anchor anchor) {
    }

    /**
     * 프로필 원의 중심과 반지름 — <b>썸네일 대비 0~1 비율</b>
     * ({@code x}·{@code r} 은 가로, {@code y} 는 세로 기준).
     */
    public record Anchor(double x, double y, double r) {
    }
}
