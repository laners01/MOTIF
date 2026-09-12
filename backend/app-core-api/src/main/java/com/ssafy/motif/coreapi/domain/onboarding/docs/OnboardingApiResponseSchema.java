package com.ssafy.motif.coreapi.domain.onboarding.docs;

import com.ssafy.motif.common.response.ApiResult;
import com.ssafy.motif.coreapi.domain.onboarding.dto.OnboardingResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * <b>Swagger 전용</b> — §3.3 성공 응답 봉투의 스키마 표현. 런타임에는 쓰이지 않는다.
 *
 * <p>이유는 {@link com.ssafy.motif.coreapi.global.docs.FailResponseSchema} javadoc 참조.
 * 성공 응답은 {@code data} 타입이 엔드포인트마다 달라 공용화할 수 없다.
 */
@Schema(name = "OnboardingApiResponse", description = "온보딩 완료 성공 응답")
public record OnboardingApiResponseSchema(

        @Schema(description = "항상 SUCCESS", example = "SUCCESS")
        ApiResult result,

        @Schema(description = "성공은 SUCCESS 고정 (명세 §1.2)", example = "SUCCESS")
        String code,

        @Schema(description = "성공은 고정 문구 (명세 §1.2)", example = "요청이 성공했습니다.")
        String message,

        OnboardingResponse data,

        @Schema(description = "ISO-8601 UTC, 밀리초 3자리 (명세 §1.3)", example = "2026-09-10T00:00:00.000Z")
        Instant timestamp
) {
}
