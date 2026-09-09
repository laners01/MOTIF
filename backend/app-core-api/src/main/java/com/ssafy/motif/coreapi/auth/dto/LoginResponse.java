package com.ssafy.motif.coreapi.auth.dto;

/**
 * 명세 §2.1 성공 응답의 {@code data}.
 *
 * @param isNewUser 이번 로그인 트랜잭션에서 {@code users} 행이 신규 생성됨.
 *                  <b>온보딩 노출 판정의 유일한 근거</b>다 (2026-09-02 회의 확정 —
 *                  구 {@code needsOnboarding} 필드는 삭제됐다).
 */
public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        boolean isNewUser,
        UserSummaryResponse user
) {

    private static final String BEARER = "Bearer";

    public static LoginResponse of(String accessToken, String refreshToken, long expiresIn,
                                   boolean isNewUser, UserSummaryResponse user) {
        return new LoginResponse(accessToken, refreshToken, BEARER, expiresIn, isNewUser, user);
    }
}
