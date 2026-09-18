package com.ssafy.motif.coreapi.domain.order.service;

import lombok.Getter;

/**
 * 토스가 이 주문의 결제를 <b>확정 거절</b>했다 (API 명세 §12.2 3단계).
 *
 * <p>{@code BusinessException} 이 아닌 이유 — 승인 트랜잭션을 <b>되돌린 뒤</b> 별도 트랜잭션으로
 * {@code FAILED} 를 기록해야 해서, 트랜잭션 안에서 바로 응답 예외로 바꾸면 안 된다.
 * {@link OrderConfirmTransaction#confirm} 이 던지고(롤백), {@link OrderConfirmService} 가 받아
 * 기록한 뒤 402 {@code PAYMENT_FAILED} 로 바꾼다.
 *
 * <p>기록에 필요한 것을 들고 다닌다 — 주문 PK, 콜백의 {@code paymentKey}, 토스 코드·메시지.
 */
@Getter
class PaymentRejectedException extends RuntimeException {

    private final Long orderSeq;
    private final String paymentKey;
    private final String code;
    private final String tossMessage;

    PaymentRejectedException(Long orderSeq, String paymentKey, String code, String tossMessage) {
        super("toss rejected orderSeq=" + orderSeq + " code=" + code + ": " + tossMessage);
        this.orderSeq = orderSeq;
        this.paymentKey = paymentKey;
        this.code = code;
        this.tossMessage = tossMessage;
    }
}
