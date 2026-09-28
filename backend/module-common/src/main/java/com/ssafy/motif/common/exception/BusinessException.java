package com.ssafy.motif.common.exception;

import com.ssafy.motif.common.error.ErrorCode;
import lombok.Getter;

/**
 * 도메인 규칙 위반. 전역 핸들러가 {@link ErrorCode}대로 응답 봉투를 만든다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final transient ErrorCode errorCode;

    /**
     * 응답 봉투 {@code data} 에 실을 실패 상세 — 없으면 {@code null}.
     *
     * <p>명세가 "{@code data} 에 사유"라고 적은 코드들이 있다(어드민 §5
     * {@code MEDIA_INVALID} 위반 사유, {@code COMPANY_IN_USE} 참조 건수,
     * {@code EDITED_SET_INCOMPLETE} 누락 조합 …). 코드마다 예외 클래스를 만들지 않고
     * 여기 한 칸을 둔다 — 전역 핸들러가 있으면 싣고 없으면 기존과 같다.
     */
    private final transient Object data;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.getMessage(), null);
    }

    /** 로그 맥락용 메시지 오버라이드 — 응답 message는 여전히 ErrorCode의 것을 쓴다. */
    public BusinessException(ErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    /** 실패 상세를 응답 {@code data} 로 함께 내보낸다. */
    public BusinessException(ErrorCode errorCode, String message, Object data) {
        super(message);
        this.errorCode = errorCode;
        this.data = data;
    }
}
