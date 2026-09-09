package com.ssafy.motif.coreapi.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 명세 §2.1 요청 — {@code { "idToken": "google-id-token" }}. */
public record GoogleLoginRequest(
        @NotBlank(message = "idToken은 필수입니다")
        @Schema(description = "구글 Sign-In 이 발급한 ID 토큰", requiredMode = Schema.RequiredMode.REQUIRED)
        String idToken
) {
}
