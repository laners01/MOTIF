package com.ssafy.motif.coreapi.admin.challenge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 슬롯 프로필 원의 자리 — 썸네일 대비 0~1 비율 (어드민 명세 §3.7.2 · §3.7.9).
 *
 * <p>{@code x·r} 은 썸네일 가로, {@code y} 는 세로 대비 비율이다. 시안(402×874, 지름 80)의
 * 기본 반지름은 {@code 40/402 ≈ 0.0995}. 픽셀이 아니라 비율인 이유는 원본 해상도를 DB 에
 * 저장하지 않고, 안드로이드가 썸네일을 몇 px 로 그리든 곱하기만 하면 자리가 맞아야 해서다.
 * 규칙({@code 0 ≤ x,y ≤ 1 · 0 < r ≤ 0.5})은 {@code SlotRules} 가 보고, DB CHECK 가 한 번 더 막는다.
 */
@Schema(description = "슬롯 앵커 (썸네일 대비 0~1 비율)")
public record AnchorRequest(
        @NotNull Double x,
        @NotNull Double y,
        @NotNull Double r) {
}
