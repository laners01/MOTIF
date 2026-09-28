package com.ssafy.motif.coreapi.domain.dev;

import jakarta.validation.constraints.NotBlank;

/**
 * 개발용 로그인 요청. {@code oauthSubject} 는 구글 ID 토큰의 {@code sub} 자리에
 * 들어갈 임의 문자열이다 — 같은 값을 두 번 보내면 재로그인으로 동작한다.
 */
public record DevLoginRequest(
        @NotBlank(message = "oauthSubject는 필수입니다")
        String oauthSubject
) {
}
