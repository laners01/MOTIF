package com.ssafy.motif.coreapi.admin.org.dto;

import java.time.Instant;

/**
 * 그룹 목록 항목 (어드민 명세 §3.4.1) — §3.4.3 수정 응답도 이 모양이다.
 *
 * @param company     FK NOT NULL 이라 {@code null} 이 아니다
 * @param memberCount 비삭제 멤버 수 — 0 이 아니면 삭제가 409 다(§3.4.4)
 */
public record GroupSummaryResponse(
        Integer groupSeq, String groupName,
        CompanyRef company,
        long memberCount,
        boolean isDeleted, Instant regDttm
) {

    public record CompanyRef(Integer companySeq, String companyName) {
    }
}
