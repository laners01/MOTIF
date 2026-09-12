package com.ssafy.motif.coreapi.domain.auth.docs;

import com.ssafy.motif.common.response.ApiResult;
import com.ssafy.motif.coreapi.domain.auth.dto.LoginResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * <b>Swagger 전용</b> — 토큰 발급 성공 응답 봉투의 스키마 표현. 런타임에는 쓰이지 않는다.
 *
 * <p>로그인(§2.1)과 재발급(§2.2)이 <b>같은 {@code data} 형태</b>를 쓰므로 하나로 공유한다
 * (명세 §2.2 "2.1 과 동일한 토큰 쌍 + user 요약").
 *
 * <p>이유는 {@link com.ssafy.motif.coreapi.global.docs.FailResponseSchema} javadoc 참조.
 * 성공 응답은 {@code data} 타입이 엔드포인트마다 달라 실패 봉투처럼 전역 공용화할 수 없다.
 */
@Schema(name = "TokenApiResponse", description = "토큰 발급 성공 응답 (§2.1 로그인 · §2.2 재발급 공통)")
public record TokenApiResponseSchema(

        @Schema(description = "항상 SUCCESS", example = "SUCCESS")
        ApiResult result,

        @Schema(description = "성공은 SUCCESS 고정 (명세 §1.2)", example = "SUCCESS")
        String code,

        @Schema(description = "성공은 고정 문구 (명세 §1.2)", example = "요청이 성공했습니다.")
        String message,

        LoginResponse data,

        @Schema(description = "ISO-8601 UTC, 밀리초 3자리 (명세 §1.3)", example = "2026-09-10T00:00:00.000Z")
        Instant timestamp
) {
}
