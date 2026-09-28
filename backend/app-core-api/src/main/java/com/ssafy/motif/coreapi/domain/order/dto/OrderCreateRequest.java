package com.ssafy.motif.coreapi.domain.order.dto;

import com.ssafy.motif.common.error.FieldErrorResponse;
import com.ssafy.motif.domain.core.order.ProductType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.ArrayList;
import java.util.List;

/**
 * 주문 생성 요청 (API 명세 §12.1) — {@code productType} 에 따라 두 형태다.
 *
 * <p><b>금액 칸이 없다.</b> 금액은 서버가 가격 행에서 읽어 스냅샷한다 — 클라이언트가 보낸
 * 금액을 받는 순간 위젯·승인 금액과 어긋날 여지가 생긴다.
 *
 * <p>형태 검증은 {@link #shapeErrors()} 다. 필수·금지가 {@code productType} 값에 따라
 * 갈리므로 필드 애너테이션 하나로 표현되지 않고, 서비스가 이 목록을 400
 * {@code INVALID_PARAMETER} 의 {@code data} 로 그대로 내보낸다 — {@code @Valid} 실패와 같은
 * 봉투다(README §1.2).
 *
 * @param productType        {@code ASSET} | {@code PASS}
 * @param assetSeq           ASSET 일 때 필수 — 대상 에셋
 * @param artistSeq          PASS 일 때 필수 — 대상 아티스트
 * @param passDurationMonths PASS 일 때 필수 — 개월 수. §11.4 {@code prices[].durationMonths}
 *                           중 하나여야 하며, 아니면 서버가 409 {@code PRODUCT_NOT_ON_SALE} 다
 */
@Schema(description = "주문 생성 요청 (§12.1) — ASSET 과 PASS 두 형태")
public record OrderCreateRequest(

        @Schema(description = "상품 종류", example = "ASSET")
        @NotNull
        ProductType productType,

        @Schema(description = "ASSET 일 때 필수 — 대상 에셋. PASS 면 보내지 않는다", example = "21")
        Integer assetSeq,

        @Schema(description = "PASS 일 때 필수 — 대상 아티스트. ASSET 이면 보내지 않는다", example = "3")
        Integer artistSeq,

        @Schema(description = "PASS 일 때 필수 — 개월 수. §11.4 prices[].durationMonths 중 하나",
                example = "1")
        @Positive
        Short passDurationMonths
) {

    /**
     * 상품 형태 검증 — DB CHECK {@code ck_orders_product_shape} 와 같은 모양이다.
     *
     * <p>ASSET 이면 {@code assetSeq} 가 있어야 하고 PASS 칸은 없어야 한다. PASS 면 반대다.
     * 반대 형태의 칸을 <b>무시하지 않고 거절하는</b> 이유는, 무시하면 "ASSET 인데
     * artistSeq 를 보냈다" 는 클라이언트 버그가 조용히 통과해 나중에 다른 상품이 결제되는
     * 사고로 이어지기 때문이다.
     *
     * @return 위반 필드 목록. 비어 있으면 형태가 맞다. {@code productType} 이 없으면
     *         비어 있다 — 그쪽은 {@code @NotNull} 이 잡는다
     */
    public List<FieldErrorResponse> shapeErrors() {
        if (productType == null) {
            return List.of();
        }
        List<FieldErrorResponse> errors = new ArrayList<>();
        switch (productType) {
            case ASSET -> {
                required(errors, "assetSeq", assetSeq);
                forbidden(errors, "artistSeq", artistSeq);
                forbidden(errors, "passDurationMonths", passDurationMonths);
            }
            case PASS -> {
                required(errors, "artistSeq", artistSeq);
                required(errors, "passDurationMonths", passDurationMonths);
                forbidden(errors, "assetSeq", assetSeq);
            }
        }
        return List.copyOf(errors);
    }

    private void required(List<FieldErrorResponse> errors, String field, Object value) {
        if (value == null) {
            errors.add(new FieldErrorResponse(field, productType + " 주문에 필수입니다."));
        }
    }

    private void forbidden(List<FieldErrorResponse> errors, String field, Object value) {
        if (value != null) {
            errors.add(new FieldErrorResponse(field, productType + " 주문에는 보내지 않습니다."));
        }
    }
}
