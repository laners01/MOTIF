package com.ssafy.motif.coreapi.domain.order.dto;

import com.ssafy.motif.domain.core.order.Order;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * 주문 생성 응답 (API 명세 §12.1) — 이 값들이 그대로 <b>토스 결제 위젯 파라미터</b>다.
 *
 * <p>{@code orderSeq} 를 내보내지 않는다. 외부에는 {@code orderUuid} 만 산다
 * ({@code Order} javadoc).
 *
 * <p><b>{@code amount} 는 문자열이다.</b> {@code NUMERIC(10,2)} 를 부동소수로 실어 보내면
 * 클라이언트 언어에 따라 {@code 3900.00} 이 {@code 3899.9999…} 로 읽히고, 그 값이 위젯을
 * 거쳐 승인(§12.2)의 금액 대조에서 어긋난다(§11.2 와 같은 계약).
 */
@Schema(description = "주문 생성 응답 (§12.1) — 그대로 토스 결제 위젯 파라미터다")
public record OrderCreateResponse(

        @Schema(description = "주문 식별자 — 토스 orderId 로 그대로 쓴다",
                example = "e3b0c442-98fc-4c14-a1b2-3d4e5f678901")
        UUID orderUuid,

        @Schema(description = "주문명 — 결제창·영수증에 표시", example = "카리나 모티프 에셋")
        String orderName,

        @Schema(description = "금액 — 문자열. 위젯에도 이 값을 그대로 넘긴다", example = "3900.00")
        String amount,

        @Schema(description = "통화", example = "KRW")
        String currency,

        @Schema(description = "결제 가능 만료 시각 — 생성 + 30분")
        Instant expiresDttm) {

    /** 저장된 주문 + 주문명. 주문명은 {@code orders} 에 칼럼이 없어 호출부가 만들어 넘긴다. */
    public static OrderCreateResponse of(Order order, String orderName) {
        // toPlainString — 지수 표기를 내지 않는다(§11.2 와 같은 계약)
        return new OrderCreateResponse(
                order.getOrderUuid(),
                orderName,
                order.getAmount().toPlainString(),
                order.getCurrency(),
                order.getExpiresDttm());
    }
}
