package com.ssafy.motif.media.encode;

/** 인코딩·패키징 실패. 부분 산출물은 호출자가 지운다 (ADR-0010 D4 실패 처리). */
public class MediaEncodingException extends RuntimeException {

    public MediaEncodingException(String message) {
        super(message);
    }

    public MediaEncodingException(String message, Throwable cause) {
        super(message, cause);
    }
}
