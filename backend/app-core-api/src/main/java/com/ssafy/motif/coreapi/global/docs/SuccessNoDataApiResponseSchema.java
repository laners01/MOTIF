package com.ssafy.motif.coreapi.global.docs;

import com.ssafy.motif.common.response.ApiResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * <b>Swagger 전용</b> — 본문 없는 성공 응답 봉투. 런타임에는 쓰이지 않는다.
 *
 * <p>명세 §1.2 는 "본문 없는 성공도 {@code data: null} 로 봉투를 유지한다"고 규정한다.
 * 이 형태는 엔드포인트가 달라도 같으므로 {@link FailResponseSchema} 처럼 하나를 공용한다
 * (§2.3 로그아웃, §8.6 관심 없음, §8.7 신고, §14.1 디바이스 토큰 등).
 *
 * <p>좋아요 토글 4종은 여기 해당하지 않는다 — 그쪽은 봉투 자체가 없는
 * {@code 204 No Content} 예외다(명세 §1.2).
 */
@Schema(name = "SuccessNoDataResponse", description = "본문 없는 성공 응답 봉투 (명세 §1.2)")
public record SuccessNoDataApiResponseSchema(

        @Schema(description = "항상 SUCCESS", example = "SUCCESS")
        ApiResult result,

        @Schema(description = "성공은 SUCCESS 고정", example = "SUCCESS")
        String code,

        @Schema(description = "성공은 고정 문구", example = "요청이 성공했습니다.")
        String message,

        @Schema(description = "항상 null", nullable = true)
        Object data,

        @Schema(description = "ISO-8601 UTC, 밀리초 3자리 (명세 §1.3)", example = "2026-09-10T00:00:00.000Z")
        Instant timestamp
) {
}
