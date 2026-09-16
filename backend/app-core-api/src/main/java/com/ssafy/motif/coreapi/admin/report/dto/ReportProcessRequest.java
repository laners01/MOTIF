package com.ssafy.motif.coreapi.admin.report.dto;

import com.ssafy.motif.domain.core.report.ReportStatus;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * §3.13.3 인용/기각 요청.
 *
 * <p>{@code RECEIVED} 는 받지 않는다 — "접수로 되돌리기" 는 명세에 없는 전이고,
 * 허용하면 처리자·처리 시각을 지워야 하는데 CHECK 제약({@code ck_reports_processed})이
 * 그 조합을 막는다.
 */
public record ReportProcessRequest(
        @NotNull(message = "decision 은 ACCEPTED 또는 REJECTED 다")
        @Schema(description = "ACCEPTED(인용) 또는 REJECTED(기각)", allowableValues = {"ACCEPTED", "REJECTED"})
        ReportStatus decision) {

    public boolean isAccepted() {
        return decision == ReportStatus.ACCEPTED;
    }

    public boolean isTerminal() {
        return decision == ReportStatus.ACCEPTED || decision == ReportStatus.REJECTED;
    }
}
