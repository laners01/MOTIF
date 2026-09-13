package com.ssafy.motif.coreapi.admin.challenge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 슬롯 인물 사각형 — 원본 px, 좌상단 원점 (어드민 명세 §3.7.2).
 *
 * <p>값은 AI팀이 산출해 넘긴 것을 어드민이 옮겨 적는다(회의 확정). 규칙
 * ({@code 우하단 > 좌상단 ≥ 0})은 {@code SlotRules} 가 보고, DB CHECK 가 한 번 더 막는다.
 */
@Schema(description = "슬롯 bbox (원본 px, 좌상단 원점)")
public record BboxRequest(
        @NotNull Integer leftTopX,
        @NotNull Integer leftTopY,
        @NotNull Integer rightBottomX,
        @NotNull Integer rightBottomY) {
}
