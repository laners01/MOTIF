package com.ssafy.motif.coreapi.admin.report.dto;

import com.ssafy.motif.domain.core.report.ReportStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * §3.13.3 성공 응답.
 *
 * @param targetDeleted 이번 처리로 <b>또는 이전에</b> 대상이 삭제된 상태인가.
 *                      "이번에 지웠는가" 가 아니다 — 멱등이라 이미 지워진 대상을
 *                      인용해도 화면에는 같은 결과가 보여야 한다
 * @param sideEffects   후처리 실감소분. 이미 삭제된 대상이면 전부 0 이다
 */
@Schema(description = "신고 처리 결과 (§3.13.3)")
public record ReportProcessResponse(
        Long reportSeq,
        ReportStatus status,
        boolean targetDeleted,
        SideEffects sideEffects) {

    @Schema(description = "인용 후처리로 실제 움직인 수치")
    public record SideEffects(
            @Schema(description = "챌린지 participant_count 변화량. 최초 삭제일 때만 -1")
            int participantCountDelta,

            @Schema(description = "연쇄 삭제된 댓글 수. 댓글 대상은 §9 미구현이라 항상 0")
            int deletedCommentCount) {

        public static final SideEffects NONE = new SideEffects(0, 0);
    }
}
