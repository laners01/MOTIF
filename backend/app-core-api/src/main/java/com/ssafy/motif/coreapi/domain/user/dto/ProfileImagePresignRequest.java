package com.ssafy.motif.coreapi.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * 프로필 이미지 업로드 URL 발급 요청 (API 명세 §4.3).
 *
 * <p><b>원본 명세는 본문이 없다.</b> 조건을 받도록 바꾼 이유는 그것이 <b>서명에
 * 박히기 때문</b>이다 — {@code Content-Type}·{@code Content-Length} 를 서명 대상에
 * 넣으면 클라이언트가 다른 값으로 PUT 할 때 S3 가 거부한다. 본문이 없으면 발급된
 * URL 로 <b>아무 타입이든 아무 크기든 {@code profiles/} 에 올릴 수 있고</b>, 바이트가
 * 서버를 지나가지 않으므로 사후에 확인할 방법도 없다. 어드민 §1.4 가 같은 근거로
 * 이미 그렇게 한다.
 *
 * @param contentType   올릴 이미지의 미디어 타입. 허용 목록 밖이면 400
 * @param contentLength 바이트 수. 상한을 넘으면 400
 */
public record ProfileImagePresignRequest(

        @NotBlank(message = "contentType은 필수입니다")
        @Schema(description = "올릴 이미지의 Content-Type. PUT 할 때 같은 값을 보내야 한다",
                example = "image/jpeg", requiredMode = Schema.RequiredMode.REQUIRED)
        String contentType,

        @Positive(message = "contentLength는 1 이상이어야 합니다")
        @Schema(description = "올릴 이미지의 바이트 수. PUT 할 때 같은 값을 보내야 한다",
                example = "204800", requiredMode = Schema.RequiredMode.REQUIRED)
        long contentLength
) {
}
