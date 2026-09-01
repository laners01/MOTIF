package com.ssafy.motif.common.response;

import com.ssafy.motif.common.error.ErrorCode;

import java.time.Instant;

/**
 * 모든 REST 응답의 공통 봉투 (MODERA 응답 관례 계승 — ADR-0004).
 *
 * <p>timestamp는 ISO-8601 UTC로 직렬화된다(Boot 4 기본 Jackson 3, camelCase).
 * MQ 경계의 snake_case 규약과는 무관하다 — 이 봉투는 REST 전용이다.
 *
 * @param result  SUCCESS / FAIL
 * @param code    실패 시 {@link ErrorCode#getCode()} (성공 시 null)
 * @param message 실패 시 사용자 표시용 메시지 (성공 시 null)
 * @param data    성공 payload, 또는 실패 상세(예: 필드 오류 리스트)
 */
public record ApiResponse<T>(
        ApiResult result,
        String code,
        String message,
        T data,
        Instant timestamp
) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(ApiResult.SUCCESS, null, null, data, Instant.now());
    }

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
