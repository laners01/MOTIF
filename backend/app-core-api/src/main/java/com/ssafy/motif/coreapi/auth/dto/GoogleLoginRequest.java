package com.ssafy.motif.coreapi.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** 명세 §2.1 요청 — {@code { "idToken": "google-id-token" }}. */
public record GoogleLoginRequest(
        @NotBlank(message = "idToken은 필수입니다")
        String idToken
) {
}
