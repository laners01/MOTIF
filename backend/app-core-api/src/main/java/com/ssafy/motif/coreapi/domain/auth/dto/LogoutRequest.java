package com.ssafy.motif.coreapi.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 명세 §2.3 요청 — {@code { "refreshToken": "..." }}. 이 기기가 보관 중인 토큰을 보낸다. */
public record LogoutRequest(
        @NotBlank(message = "refreshToken은 필수입니다")
        @Schema(description = "이 기기의 refresh token. 이 토큰 하나만 폐기된다",
                example = "s7Fq2vXk9pLmNbVcZxAsDfGhJkLqWeRtYuIoP1234567",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String refreshToken
) {
}
