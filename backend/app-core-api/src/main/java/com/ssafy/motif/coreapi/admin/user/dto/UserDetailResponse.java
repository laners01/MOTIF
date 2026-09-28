package com.ssafy.motif.coreapi.admin.user.dto;

import com.ssafy.motif.domain.core.auth.AccountStatus;
import com.ssafy.motif.domain.core.user.UserRole;

import java.time.Instant;
import java.util.List;

/**
 * 회원 상세 (어드민 명세 §3.14.2).
 *
 * <p>목록 항목에 통계 6종과 보유 패스를 더한 모양이다 — 화면이 목록에서 상세로 넘어갈 때
 * 같은 필드명을 다시 쓴다.
 */
public record UserDetailResponse(
        Integer userSeq, String nickname, UserRole role, String profileImageUrl,
        AccountStatus accountStatus, boolean isDeleted, Instant regDttm,
        Stats stats, List<ActivePass> activePasses
) {

    /**
     * @param reportedCount 이 회원의 콘텐츠가 신고된 건수
     * @param reportCount   이 회원이 신고한 건수
     */
    public record Stats(
            long videoCount, long subscriptionCount, long activePurchaseCount,
            long activePassCount, long reportedCount, long reportCount
    ) {
    }

    public record ActivePass(Integer artistSeq, String artistName, Instant expiresDttm) {
    }
}
