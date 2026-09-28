package com.ssafy.motif.coreapi.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 프로필 이미지 반영 요청 (API 명세 §4.4).
 *
 * <p>§4.3 이 준 키를 그대로 보낸다. <b>presigned PUT 업로드를 마친 뒤</b> 호출한다 —
 * 서버가 S3 에 실제로 있는지 확인하므로(HEAD) 올리기 전에 부르면 409 다.
 */
public record ProfileImageUpdateRequest(

        @NotBlank(message = "imageKey는 필수입니다")
        @Schema(description = "§4.3 이 발급한 키", example = "profiles/3f9c1a2b.jpg",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String imageKey
) {
}
