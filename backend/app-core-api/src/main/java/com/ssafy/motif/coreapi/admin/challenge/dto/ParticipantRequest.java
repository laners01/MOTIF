package com.ssafy.motif.coreapi.admin.challenge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * 교체 가능한 인물 슬롯 1개 (어드민 명세 §3.7.2).
 *
 * <p><b>배열 순서가 화면 좌→우다.</b> 서버가 그 순서대로 {@code participant_mask = 1 << index}
 * 를 부여한다 — 요청에 마스크 칸이 없는 것이 의도다(수기 비트 입력 → 오류 차단).
 */
@Schema(description = "인물 슬롯 (배열 순서 = 화면 좌→우)")
public record ParticipantRequest(
        @NotNull Integer artistSeq,
        @Valid @NotNull AnchorRequest anchor) {
}
