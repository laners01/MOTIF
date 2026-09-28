package com.ssafy.motif.coreapi.admin.report.dto;

import com.ssafy.motif.coreapi.admin.video.dto.AdminVideoItem;
import com.ssafy.motif.domain.core.report.ReportStatus;
import com.ssafy.motif.domain.core.report.ReportTargetType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * 신고 목록·상세 항목 (어드민 명세 §3.13.1·§3.13.2).
 *
 * @param target 대상 미리보기. <b>지원하지 않는 종류면 null 이다</b> — 댓글 3종은 §9
 *               댓글이 아직 없어 조립할 수 없다. 화면이 null 을 보고 "미지원" 을
 *               표시한다. 항목 자체를 숨기지 않는 이유는, 숨기면 처리 못 하는 신고가
 *               목록에서 사라져 영원히 방치되기 때문이다
 * @param sameTargetReportCount 같은 대상에 쌓인 신고 수. 여러 명이 신고한 것을 먼저
 *                              보라는 신호다
 */
@Schema(description = "신고 항목 (§3.13.1·§3.13.2)")
public record ReportItem(
        Long reportSeq,
        ReportTargetType targetType,
        String reasonCode,
        ReportStatus status,
        Reporter reporter,

        @Schema(description = "대상 미리보기. 댓글 대상은 §9 미구현이라 null 이다")
        AdminVideoItem target,

        long sameTargetReportCount,
        Instant regDttm,
        Integer processedBy,
        Instant processedDttm) {

    @Schema(description = "신고자")
    public record Reporter(Integer userSeq, String nickname) {
    }
}
