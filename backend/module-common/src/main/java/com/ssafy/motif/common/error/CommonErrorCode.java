package com.ssafy.motif.common.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 도메인에 속하지 않는 공통 에러 코드. 접두사 {@code C}. */
@Getter
@RequiredArgsConstructor
public enum CommonErrorCode implements ErrorCode {

    INVALID_INPUT(400, "C001", "요청 값이 올바르지 않습니다."),
    INTERNAL_ERROR(500, "C999", "서버 내부 오류가 발생했습니다.");

    private final int status;
    private final String code;
    private final String message;
}
