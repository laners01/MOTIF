package com.ssafy.motif.coreapi.domain.order.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 승인 요청의 정규화·대조 도우미 (API 명세 §12.2).
 *
 * <p>금액 대조가 {@code compareTo} 여야 한다는 계약이 여기서 고정된다 — 토스 콜백은 소수부
 * 없는 {@code 3900} 을 주고 우리 스냅샷은 {@code 3900.00} 이다. 문자열이나 {@code equals} 로
 * 비교했다면 모든 승인이 {@code AMOUNT_MISMATCH} 다.
 */
class OrderConfirmRequestTest {

    private static final UUID ORDER_UUID = UUID.fromString("e3b0c442-98fc-4c14-a1b2-3d4e5f678901");

    @Test
    @DisplayName("세 칸의 앞뒤 공백을 떼고 받는다 — 검증보다 먼저다")
    void stripsAllFields() {
        OrderConfirmRequest request = new OrderConfirmRequest(
                "  pk-1 ", " e3b0c442-98fc-4c14-a1b2-3d4e5f678901 ", " 3900.00\n");

        assertThat(request.paymentKey()).isEqualTo("pk-1");
        assertThat(request.orderId()).isEqualTo("e3b0c442-98fc-4c14-a1b2-3d4e5f678901");
        assertThat(request.amount()).isEqualTo("3900.00");
    }

    @Test
    @DisplayName("orderId 는 경로의 UUID 와 같아야 한다 — 대소문자는 가리지 않는다")
    void pointsToComparesUuidText() {
        assertThat(request("E3B0C442-98FC-4C14-A1B2-3D4E5F678901", "3900").pointsTo(ORDER_UUID)).isTrue();
        assertThat(request("7c9e6679-7425-40de-944b-e07fc1f90ae7", "3900").pointsTo(ORDER_UUID)).isFalse();
        assertThat(new OrderConfirmRequest("pk", null, "3900").pointsTo(ORDER_UUID)).isFalse();
    }

    @Test
    @DisplayName("amount 는 BigDecimal 이다 — 콜백의 3900 과 스냅샷 3900.00 이 compareTo 로 같다")
    void amountIsComparableToSnapshot() {
        BigDecimal callback = request(ORDER_UUID.toString(), "3900").amountValue().orElseThrow();

        assertThat(callback).isEqualByComparingTo(new BigDecimal("3900.00"));
        assertThat(callback).isNotEqualTo(new BigDecimal("3900.00"));   // equals 는 스케일까지 본다
    }

    @Test
    @DisplayName("숫자가 아니면 비어 있다 — 그쪽은 400 INVALID_PARAMETER 다")
    void nonNumericAmountIsEmpty() {
        assertThat(request(ORDER_UUID.toString(), "3,900").amountValue()).isEmpty();
        assertThat(request(ORDER_UUID.toString(), "삼천구백").amountValue()).isEmpty();
        assertThat(new OrderConfirmRequest("pk", ORDER_UUID.toString(), null).amountValue()).isEmpty();
    }

    private static OrderConfirmRequest request(String orderId, String amount) {
        return new OrderConfirmRequest("pk", orderId, amount);
    }
}
