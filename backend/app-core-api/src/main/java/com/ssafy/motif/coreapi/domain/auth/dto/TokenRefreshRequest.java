package com.ssafy.motif.coreapi.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 명세 §2.2 요청 — {@code { "refreshToken": "..." }}. */
public record TokenRefreshRequest(
        @NotBlank(message = "refreshToken은 필수입니다")
        @Schema(description = "§2.1·§2.2 응답으로 받은 refresh token 원문. 해석·가공하지 않고 그대로 보낸다",
                example = "s7Fq2vXk9pLmNbVcZxAsDfGhJkLqWeRtYuIoP1234567",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String refreshToken
) {
}
