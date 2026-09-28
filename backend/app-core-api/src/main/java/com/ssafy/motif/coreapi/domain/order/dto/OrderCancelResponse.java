package com.ssafy.motif.coreapi.domain.order.dto;

import com.ssafy.motif.domain.core.order.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * 승인 전 취소 응답 (API 명세 §12.3).
 *
 * <p>{@code status} 는 언제나 {@code CANCELED} 다 — 전이가 됐을 때만 이 응답이 나가고, 안 됐으면
 * 409 실패 봉투다. 칸을 두는 이유는 원본 응답 예시가 그렇게 적었기 때문이다.
 */
@Schema(description = "승인 전 취소 응답 (§12.3)")
public record OrderCancelResponse(

        @Schema(description = "주문 식별자", example = "e3b0c442-98fc-4c14-a1b2-3d4e5f678901")
        UUID orderUuid,

        @Schema(description = "주문 상태 — 성공 응답은 언제나 CANCELED", example = "CANCELED")
        OrderStatus status) {

    public static OrderCancelResponse canceled(UUID orderUuid) {
        return new OrderCancelResponse(orderUuid, OrderStatus.CANCELED);
    }
}
