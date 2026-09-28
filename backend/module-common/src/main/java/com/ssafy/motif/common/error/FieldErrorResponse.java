package com.ssafy.motif.common.error;

/**
 * {@code @Valid} 검증 실패 시 필드 단위 오류.
 * 전역 핸들러가 {@code ApiResponse.data}에 리스트로 싣는다.
 */
public record FieldErrorResponse(String field, String message) {
}
