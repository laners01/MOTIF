package com.ssafy.motif.coreapi.domain.auth.docs;

import com.ssafy.motif.common.response.ApiResult;
import com.ssafy.motif.coreapi.domain.auth.dto.LoginResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * <b>Swagger 전용</b> — §2.1 성공 응답 봉투의 스키마 표현. 런타임에는 쓰이지 않는다.
 *
 * <p>이유는 {@link com.ssafy.motif.coreapi.global.docs.FailResponseSchema} javadoc 참조.
 * 성공 응답은 {@code data} 타입이 엔드포인트마다 달라 공용화할 수 없다.
 */
@Schema(name = "LoginApiResponse", description = "구글 로그인 성공 응답")
public record LoginApiResponseSchema(

        @Schema(description = "항상 SUCCESS", example = "SUCCESS")
        ApiResult result,

        @Schema(description = "성공 시 null", nullable = true)
        String code,

        @Schema(description = "성공 시 null", nullable = true)
        String message,

        LoginResponse data,

        @Schema(description = "ISO-8601 UTC")
        Instant timestamp
) {
}
