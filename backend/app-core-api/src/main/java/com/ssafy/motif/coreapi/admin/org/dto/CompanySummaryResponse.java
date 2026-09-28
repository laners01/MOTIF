package com.ssafy.motif.coreapi.admin.org.dto;

import java.time.Instant;

/**
 * 소속사 목록 항목 (어드민 명세 §3.3.1).
 *
 * @param groupCount  비삭제 그룹 수 — 0 이 아니면 삭제가 409 다(§3.3.4)
 * @param artistCount 비삭제 아티스트 수 — 위와 같다
 */
public record CompanySummaryResponse(
        Integer companySeq, String companyName,
        long groupCount, long artistCount,
        boolean isDeleted, Instant regDttm
) {
}
