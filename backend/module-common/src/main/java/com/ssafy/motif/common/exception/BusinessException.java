package com.ssafy.motif.common.exception;

import com.ssafy.motif.common.error.ErrorCode;
import lombok.Getter;

/**
 * 도메인 규칙 위반. 전역 핸들러가 {@link ErrorCode}대로 응답 봉투를 만든다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final transient ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /** 로그 맥락용 메시지 오버라이드 — 응답 message는 여전히 ErrorCode의 것을 쓴다. */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
