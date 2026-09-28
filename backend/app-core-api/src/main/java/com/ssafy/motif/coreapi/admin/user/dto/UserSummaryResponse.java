package com.ssafy.motif.coreapi.admin.user.dto;

import com.ssafy.motif.domain.core.auth.AccountStatus;
import com.ssafy.motif.domain.core.user.UserRole;

import java.time.Instant;

/**
 * 회원 목록 항목 (어드민 명세 §3.14.1).
 *
 * @param accountStatus 아티스트는 {@code null} — 로그인 계정이 없다({@code UserListRow})
 * @param isDeleted     삭제 회원도 어드민에게는 보인다(§3.14) — 404 는 미존재일 때만이다
 */
public record UserSummaryResponse(
        Integer userSeq, String nickname, UserRole role, String profileImageUrl,
        AccountStatus accountStatus, boolean isDeleted, Instant regDttm
) {
}
