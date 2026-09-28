package com.ssafy.motif.coreapi.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 명세 §2.1 성공 응답의 {@code data}.
 *
 * @param isNewUser 이번 로그인 트랜잭션에서 {@code users} 행이 신규 생성됨.
 *                  <b>온보딩 노출 판정의 유일한 근거</b>다 (2026-09-02 회의 확정 —
 *                  구 {@code needsOnboarding} 필드는 삭제됐다).
 */
public record LoginResponse(
        @Schema(description = "서비스 access token (JWT). Authorization: Bearer <값>")
        String accessToken,

        @Schema(description = "재발급용 토큰. **불투명 문자열이며 JWT 가 아니다** — 해석하지 않고 그대로 보관·재전송한다")
        String refreshToken,

        @Schema(description = "고정값", example = "Bearer")
        String tokenType,

        @Schema(description = "access token 유효 기간(초)", example = "3600")
        long expiresIn,

        @Schema(description = "이번 요청에서 users 행이 새로 생겼는지. 온보딩 노출 판정의 유일한 근거")
        boolean isNewUser,

        UserSummaryResponse user
) {

    private static final String BEARER = "Bearer";

    public static LoginResponse of(String accessToken, String refreshToken, long expiresIn,
                                   boolean isNewUser, UserSummaryResponse user) {
        return new LoginResponse(accessToken, refreshToken, BEARER, expiresIn, isNewUser, user);
    }
}
