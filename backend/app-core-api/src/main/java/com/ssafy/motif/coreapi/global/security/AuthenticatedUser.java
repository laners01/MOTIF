package com.ssafy.motif.coreapi.global.security;

import com.ssafy.motif.domain.core.user.UserRole;

/**
 * 인증된 요청의 principal — access token claims에서만 만든다.
 *
 * <p>SCG가 채우던 {@code X-User-Seq}·{@code X-User-Role} 헤더는 없다(ADR-0009 D5).
 * 앱이 토큰을 직접 검증하므로 헤더를 신뢰할 필요가 없다.
 */
public record AuthenticatedUser(Integer userSeq, UserRole role) {
}
