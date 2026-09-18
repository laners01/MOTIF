package com.ssafy.motif.coreapi.domain.order.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ssafy.motif.domain.core.order.OrderStatus;
import com.ssafy.motif.domain.core.order.ProductType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * 결제 승인 응답 (API 명세 §12.2).
 *
 * <p>{@code productType} 에 따라 {@code pass} 또는 {@code asset} 중 <b>하나만</b> 실린다.
 * {@code @JsonInclude(NON_NULL)} 로 안 쓰는 쪽은 응답에서 빠진다 — §6.3 {@code ActivityItem}
 * 과 같은 방식이다. 클라이언트는 {@code productType} 으로 분기한다.
 *
 * <p>{@code status} 는 언제나 {@code PAID} 다 — 승인이 끝나야 이 응답이 나가고, 실패는 전부
 * 실패 봉투다. 칸을 두는 이유는 원본 응답 예시가 그렇게 적었기 때문이다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "결제 승인 응답 (§12.2) — productType 에 따라 pass 또는 asset 중 하나만")
public record OrderConfirmResponse(

        @Schema(description = "주문 식별자", example = "e3b0c442-98fc-4c14-a1b2-3d4e5f678901")
        UUID orderUuid,

        @Schema(description = "주문 상태 — 성공 응답은 언제나 PAID", example = "PAID")
        OrderStatus status,

        @Schema(description = "상품 종류 — 이 값으로 pass / asset 을 가른다", example = "PASS")
        ProductType productType,

        @Schema(description = "PASS 일 때만 — 새 이용 기간", nullable = true)
        Pass pass,

        @Schema(description = "ASSET 일 때만 — 보유하게 된 에셋", nullable = true)
        Asset asset) {

    public static OrderConfirmResponse forPass(UUID orderUuid, Instant expiresDttm) {
        return new OrderConfirmResponse(orderUuid, OrderStatus.PAID, ProductType.PASS,
                new Pass(expiresDttm), null);
    }

    public static OrderConfirmResponse forAsset(UUID orderUuid, Integer assetSeq) {
        return new OrderConfirmResponse(orderUuid, OrderStatus.PAID, ProductType.ASSET,
                null, new Asset(assetSeq));
    }

    @Schema(description = "PASS 승인 결과")
    public record Pass(
            @Schema(description = "이용 기간 만료 — 승인 시각 + 개월 수", example = "2026-10-02T06:00:00.000Z")
            Instant expiresDttm) {
    }

    @Schema(description = "ASSET 승인 결과")
    public record Asset(
            @Schema(description = "보유하게 된 에셋", example = "21")
            Integer assetSeq) {
    }
}
