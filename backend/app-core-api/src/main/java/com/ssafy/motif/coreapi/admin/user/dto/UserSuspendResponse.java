package com.ssafy.motif.coreapi.admin.user.dto;

import com.ssafy.motif.domain.core.auth.AccountStatus;

/**
 * 회원 정지 결과 (어드민 명세 §3.14.3).
 *
 * @param revokedTokenCount 이번 정지로 실제 폐기된 refresh 토큰 수 —
 *                          이미 폐기돼 있던 것은 세지 않는다(기기 수와 다를 수 있다)
 */
public record UserSuspendResponse(
        Integer userSeq, AccountStatus accountStatus, int revokedTokenCount
) {
}
