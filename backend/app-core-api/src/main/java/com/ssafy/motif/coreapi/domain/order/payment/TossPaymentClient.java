package com.ssafy.motif.coreapi.domain.order.payment;

import java.math.BigDecimal;

/**
 * 토스페이먼츠 코어 API 중 결제 승인(§12.2)이 쓰는 두 호출.
 *
 * <p>인터페이스로 둔 이유는 테스트다 — 승인 서비스의 local-db 테스트가 이 빈을 가짜로 바꿔
 * 거절·결과 불명·복구 경로를 돈 없이 돈다. 구현은 {@link RestClientTossPaymentClient} 하나다.
 *
 * <p>두 메서드의 실패 계약은 같다 — 확정 거절은 {@link TossApiException}, 답을 못 받았거나
 * 믿을 수 없으면 {@link TossUnavailableException}.
 */
public interface TossPaymentClient {

    /**
     * 결제 승인 — {@code POST /v1/payments/confirm}.
     *
     * @param paymentKey 토스 콜백의 {@code paymentKey}
     * @param orderId    우리 {@code orderUuid} 문자열 — 위젯을 열 때 넘긴 값과 같아야 한다
     * @param amount     주문 스냅샷 금액. 토스에는 KRW 정수로 나간다
     * @return 승인된 결제 — {@code status=DONE}
     * @throws TossApiException         토스가 거절함 (카드사 거절 · 세션 만료 · 이미 처리됨 …)
     * @throws TossUnavailableException 답을 받지 못함 · 5xx · 키 오류 · 일시 오류
     */
    TossPayment confirm(String paymentKey, String orderId, BigDecimal amount);

    /**
     * 결제 조회 — {@code GET /v1/payments/{paymentKey}}. {@code ALREADY_PROCESSED_PAYMENT} 복구에 쓴다.
     *
     * @throws TossApiException         토스가 거절함 (없는 결제 …)
     * @throws TossUnavailableException 답을 받지 못함
     */
    TossPayment find(String paymentKey);
}
