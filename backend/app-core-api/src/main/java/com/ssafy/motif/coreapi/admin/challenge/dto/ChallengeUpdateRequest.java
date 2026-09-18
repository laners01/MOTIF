package com.ssafy.motif.coreapi.admin.challenge.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 챌린지 부분 수정 (어드민 명세 §3.7.4) — <b>소개글·곡만</b> 바뀐다.
 *
 * <p>{@code videoKey}·{@code artistSeq} 는 바꾸기 위해서가 아니라 <b>거절하기 위해</b>
 * 받는다. 명세가 "요청에 포함되면 400" 이라고 정했다(회의 확정 R1 — 원본 교체 금지).
 * 모르는 필드로 조용히 무시하면 어드민은 원본을 바꿨다고 믿고 화면을 닫는다 —
 * 앵커와 편집본이 전부 옛 원본 기준이라는 사실은 사용자가 촬영할 때 드러난다.
 * 원본을 갈아야 하면 삭제 후 재등록이다.
 */
@Schema(description = "챌린지 부분 수정 (소개글·곡)")
public record ChallengeUpdateRequest(
        String description,
        Integer songSeq,
        @Schema(description = "불변 — 보내면 400", accessMode = Schema.AccessMode.WRITE_ONLY)
        String videoKey,
        @Schema(description = "불변 — 보내면 400", accessMode = Schema.AccessMode.WRITE_ONLY)
        Integer artistSeq) {
}
