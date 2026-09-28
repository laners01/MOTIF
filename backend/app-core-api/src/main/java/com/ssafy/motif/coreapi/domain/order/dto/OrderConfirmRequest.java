package com.ssafy.motif.coreapi.domain.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * 결제 승인 요청 (API 명세 §12.2) — 토스 {@code successUrl} 콜백 파라미터를 <b>그대로</b> 싣는다.
 *
 * <p>{@code amount} 가 문자열인 이유는 §12.1 응답과 같다 — 금액을 부동소수로 옮기지 않는다.
 * 대조는 서비스가 {@link BigDecimal#compareTo} 로 한다: {@code 3900} 과 {@code 3900.00} 은 같은
 * 값이다. 토스 콜백은 소수부 없는 정수 문자열로 준다.
 *
 * <p>세 칸을 앞뒤 공백 없이 받는다 — 검증보다 먼저 일어나므로 공백만 보낸 요청은
 * {@code @NotBlank} 에 걸린다({@code CommentCreateRequest} 와 같은 이유).
 *
 * @param paymentKey 토스 결제 키 — 최대 200자(토스 계약)
 * @param orderId    토스가 돌려준 주문 식별자 — 경로의 {@code orderUuid} 와 같아야 한다
 * @param amount     토스가 돌려준 금액 문자열
 */
@Schema(description = "결제 승인 요청 (§12.2) — 토스 successUrl 콜백 파라미터 그대로")
public record OrderConfirmRequest(

        @Schema(description = "토스 paymentKey", example = "toss-payment-key")
        @NotBlank
        @Size(max = 200)
        String paymentKey,

        @Schema(description = "토스 orderId — 경로의 orderUuid 와 같은 값",
                example = "e3b0c442-98fc-4c14-a1b2-3d4e5f678901")
        @NotBlank
        String orderId,

        @Schema(description = "토스 콜백 amount — 문자열. 주문 스냅샷과 BigDecimal.compareTo 로 대조한다",
                example = "3900.00")
        @NotBlank
        String amount
) {

    public OrderConfirmRequest {
        paymentKey = paymentKey == null ? null : paymentKey.strip();
        orderId = orderId == null ? null : orderId.strip();
        amount = amount == null ? null : amount.strip();
    }

    /** 경로의 주문을 가리키는가 — UUID 문자열 비교, 대소문자는 가리지 않는다. */
    public boolean pointsTo(UUID orderUuid) {
        return orderId != null && orderId.equalsIgnoreCase(orderUuid.toString());
    }

    /** 금액을 숫자로. 숫자가 아니면 비어 있다 — 그쪽은 400 {@code INVALID_PARAMETER} 다. */
    public Optional<BigDecimal> amountValue() {
        if (amount == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new BigDecimal(amount));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
