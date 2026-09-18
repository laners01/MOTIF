package com.ssafy.motif.coreapi.domain.order.payment;

/**
 * 토스의 답을 <b>받지 못했거나 믿을 수 없다</b> — 타임아웃 · 연결 실패 · 5xx · 우리 쪽 키 오류
 * · 토스가 "잠시 후 다시" 라고 답한 경우({@code PROVIDER_ERROR}).
 *
 * <p>결제가 됐는지 안 됐는지 모르는 상태라 주문을 {@code FAILED} 로 기록하지 않는다. 전역
 * 핸들러가 500 {@code INTERNAL_ERROR} 로 내고 주문은 {@code PENDING} 에 남아, 클라이언트가
 * 같은 {@code paymentKey} 로 다시 부르면 토스가 멱등하게 답한다(이미 승인됐으면
 * {@code ALREADY_PROCESSED_PAYMENT} → 복구 경로).
 */
public class TossUnavailableException extends RuntimeException {

    public TossUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
