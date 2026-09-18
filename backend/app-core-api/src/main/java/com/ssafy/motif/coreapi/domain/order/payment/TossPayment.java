package com.ssafy.motif.coreapi.domain.order.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * 토스 결제 객체 중 <b>우리가 읽는 칸만</b> (승인 응답 · 결제 조회 응답 공통).
 *
 * <p>토스 응답은 카드·가상계좌·영수증 등 수십 칸이지만 승인 판정에 필요한 것은 이 다섯이다.
 * 모르는 칸은 무시한다 — 토스가 칸을 더해도 깨지지 않게.
 *
 * @param paymentKey  결제 키 — 콜백으로 받은 값과 같아야 한다
 * @param orderId     우리가 넘긴 {@code orderUuid} 문자열
 * @param status      {@code DONE} 이 승인 완료다. 그 밖({@code READY}·{@code IN_PROGRESS}·
 *                    {@code CANCELED} …)은 승인으로 치지 않는다
 * @param totalAmount 승인 금액 — 주문 스냅샷과 {@code compareTo} 로 대조한다
 * @param method      결제수단 표시 문자열(카드·간편결제 …). 로그용
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TossPayment(String paymentKey, String orderId, String status, BigDecimal totalAmount, String method) {

    public static final String STATUS_DONE = "DONE";

    public boolean isDone() {
        return STATUS_DONE.equals(status);
    }
}
