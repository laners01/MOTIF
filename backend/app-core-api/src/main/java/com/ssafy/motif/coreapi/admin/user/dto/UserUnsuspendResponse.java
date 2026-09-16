package com.ssafy.motif.coreapi.admin.user.dto;

import com.ssafy.motif.domain.core.auth.AccountStatus;

/**
 * 회원 정지 해제 결과 (어드민 명세 §3.14.4).
 *
 * <p>{@code revokedTokenCount} 가 없다 — 해제는 토큰을 복구하지 않는다. 정지 응답과 한
 * record 로 합치고 그 칸을 {@code null} 로 두면 명세에 없는 키가 응답에 생긴다.
 */
public record UserUnsuspendResponse(Integer userSeq, AccountStatus accountStatus) {
}
