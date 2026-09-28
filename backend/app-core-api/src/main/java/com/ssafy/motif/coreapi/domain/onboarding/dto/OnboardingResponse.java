package com.ssafy.motif.coreapi.domain.onboarding.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 명세 §3.3 성공 응답의 {@code data}. */
public record OnboardingResponse(

        @Schema(description = "실제로 생성된 구독 수. 요청에 중복이 있었다면 제거 후 개수", example = "3")
        int subscribedCount,

        @Schema(description = "확정된 닉네임", example = "모티프러버")
        String nickname
) {
}
