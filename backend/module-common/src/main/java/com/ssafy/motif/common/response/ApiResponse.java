package com.ssafy.motif.common.response;

import com.ssafy.motif.common.error.ErrorCode;

import java.time.Instant;

/**
 * 모든 REST 응답의 공통 봉투 (API 명세 §1.2 — 팀 공통 응답 규약).
 *
 * <p><b>성공도 {@code code}·{@code message}를 채운다.</b> 명세 §1.2 는 성공을
 * {@code result="SUCCESS"}, {@code code="SUCCESS"}, {@code message="요청이 성공했습니다."}
 * 로 고정한다 — 클라이언트 분기는 {@code result}·HTTP 상태로만 하고 message 는
 * 표시용으로만 쓴다는 전제의 규약이다. 성공 code 를 null 로 비우면 안드로이드가
 * 명세대로 파싱할 때 계약이 어긋난다.
 *
 * <p>{@code timestamp} 는 밀리초 3자리 UTC(`2026-09-02T06:00:00.000Z`)로 직렬화된다
 * (명세 §1.3). 포맷 고정은 이 record 가 아니라
 * {@code coreapi.global.config.JacksonConfig} 의 Instant 전역 직렬화가 담당한다 —
 * 봉투뿐 아니라 DTO 의 모든 시각 필드에 같은 규약을 적용해야 하기 때문이다.
 * MQ 경계의 snake_case·ISO 규약과는 무관하다(ADR-0005) — 이 봉투는 REST 전용이다.
 *
 * <p><b>예외: 좋아요 생성/취소 POST·DELETE 성공은 이 봉투를 반환하지 않는다</b>
 * (204 No Content — 명세 §1.2, §7.3·§8.3·§9.5·§10.2).
 *
 * @param result  SUCCESS / FAIL
 * @param code    성공은 {@link #SUCCESS_CODE} 고정, 실패는 {@link ErrorCode#getCode()}
 * @param message 성공은 {@link #SUCCESS_MESSAGE} 고정, 실패는 사용자 표시용 메시지
 * @param data    성공 payload, 또는 실패 상세(예: 필드 오류 리스트). 본문 없는 성공은 null
 */
public record ApiResponse<T>(
        ApiResult result,
        String code,
        String message,
        T data,
        Instant timestamp
) {

    /** 명세 §1.2 — 성공 code 는 이 값으로 고정한다. */
    public static final String SUCCESS_CODE = "SUCCESS";

    /** 명세 §1.2 성공 응답의 고정 문구. */
    public static final String SUCCESS_MESSAGE = "요청이 성공했습니다.";

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(ApiResult.SUCCESS, SUCCESS_CODE, SUCCESS_MESSAGE, data, Instant.now());
    }

    /** 본문 없는 성공 — 명세 §1.2 대로 {@code data: null} 로 봉투를 유지한다. */
    public static ApiResponse<Void> success() {
        return success(null);
    }

    public static <T> ApiResponse<T> fail(ErrorCode errorCode, T data) {
        return new ApiResponse<>(ApiResult.FAIL, errorCode.getCode(), errorCode.getMessage(), data, Instant.now());
    }

    public static ApiResponse<Void> fail(ErrorCode errorCode) {
        return new ApiResponse<>(ApiResult.FAIL, errorCode.getCode(), errorCode.getMessage(), null, Instant.now());
    }
}
