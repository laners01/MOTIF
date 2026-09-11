package com.ssafy.motif.coreapi.global.docs;

import com.ssafy.motif.common.response.ApiResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * <b>Swagger 전용</b> — 실패 응답 봉투의 스키마 표현. 런타임에는 쓰이지 않는다.
 *
 * <p>{@code ApiResponse<T>} 는 제네릭이라 {@code @Schema(implementation=...)} 로
 * 지목할 수 없다. 그리고 {@code @Content} 에 {@code examples} 만 주면 springdoc 이
 * 추론한 스키마를 <b>버린다</b> — 예시만 있고 타입이 없는 문서가 된다.
 * 그래서 봉투를 구체 타입으로 한 번 미러링해 둔다.
 *
 * <p>실패 봉투는 모든 엔드포인트에서 같은 모양이므로 이 하나를 공용으로 쓴다.
 * 성공 응답은 {@code data} 타입이 엔드포인트마다 달라 각 docs 패키지에서 따로 만든다.
 */
@Schema(name = "FailResponse", description = "공통 실패 응답 봉투 (명세 §1.3)")
public record FailResponseSchema(

        @Schema(description = "항상 FAIL", example = "FAIL")
        ApiResult result,

        @Schema(description = "실패 코드 — 클라이언트 분기 기준", example = "INVALID_GOOGLE_TOKEN")
        String code,

        @Schema(description = "사용자 표시용 메시지")
        String message,

        @Schema(description = "실패 상세. 검증 실패면 필드 오류 리스트, 그 밖에는 null 또는 상세 문자열", nullable = true)
        Object data,

        @Schema(description = "ISO-8601 UTC, 밀리초 3자리 (명세 §1.3)", example = "2026-09-10T00:00:00.000Z")
        Instant timestamp
) {
}
