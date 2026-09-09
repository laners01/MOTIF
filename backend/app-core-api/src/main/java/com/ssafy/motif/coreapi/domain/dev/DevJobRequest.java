package com.ssafy.motif.coreapi.domain.dev;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 개발용 합성 잡 발행 요청. */
public record DevJobRequest(
        @NotBlank(message = "jobType은 필수입니다")
        @Pattern(regexp = "MOTIF|CHALLENGE", message = "jobType은 MOTIF 또는 CHALLENGE만 허용합니다")
        String jobType
) {
}
