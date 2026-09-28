package com.ssafy.motif.coreapi.domain.order.payment;

import lombok.Getter;

/**
 * 토스가 요청을 <b>확정 거절</b>했다 — 4xx 와 {@code {code, message}} 본문.
 *
 * <p>{@link TossUnavailableException} 과 갈라 두는 것이 이 클라이언트의 요점이다. 이쪽은
 * "이 결제는 안 된다"는 답이라 주문을 {@code FAILED} 로 기록하고 402 를 내지만, 저쪽은
 * 답을 못 받은 것이라 주문을 {@code PENDING} 에 남겨 재시도를 열어 둔다(§12.2).
 *
 * <p>{@link #ALREADY_PROCESSED_PAYMENT} 만 예외적으로 거절이 아니다 — "이미 승인된 결제"라
 * 호출부가 결제 조회로 확인한 뒤 승인으로 간주한다.
 */
@Getter
public class TossApiException extends RuntimeException {

    /** 같은 paymentKey 로 이미 승인이 끝났다 — 앞선 시도가 승인 뒤 끊긴 경우의 복구 실마리. */
    public static final String ALREADY_PROCESSED_PAYMENT = "ALREADY_PROCESSED_PAYMENT";

    private final int httpStatus;
    private final String code;
    private final String tossMessage;

    public TossApiException(int httpStatus, String code, String tossMessage) {
        super("toss " + httpStatus + " " + code + ": " + tossMessage);
        this.httpStatus = httpStatus;
        this.code = code;
        this.tossMessage = tossMessage;
    }
}
