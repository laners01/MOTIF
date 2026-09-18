package com.ssafy.motif.coreapi.domain.order.dto;

import com.ssafy.motif.common.error.FieldErrorResponse;
import com.ssafy.motif.domain.core.order.ProductType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * 주문 요청의 상품 형태 검증 (API 명세 §12.1).
 *
 * <p>DB CHECK {@code ck_orders_product_shape} 와 같은 규칙을 요청 단계에서 400 으로 미리
 * 낸다. <b>반대 형태의 칸을 무시하지 않고 거절하는 것</b>이 계약이다 — 무시하면 "ASSET 인데
 * artistSeq 를 보냈다" 는 클라이언트 버그가 조용히 통과한다.
 */
class OrderCreateRequestTest {

    @Test
    @DisplayName("ASSET 은 assetSeq 만 — 형태가 맞으면 오류가 없다")
    void assetShapeIsValid() {
        assertThat(new OrderCreateRequest(ProductType.ASSET, 21, null, null).shapeErrors())
                .isEmpty();
    }

    @Test
    @DisplayName("PASS 는 artistSeq·passDurationMonths — 형태가 맞으면 오류가 없다")
    void passShapeIsValid() {
        assertThat(new OrderCreateRequest(ProductType.PASS, null, 3, (short) 1).shapeErrors())
                .isEmpty();
    }

    @Test
    @DisplayName("ASSET 인데 assetSeq 가 없으면 필수 위반이다")
    void assetRequiresAssetSeq() {
        assertThat(new OrderCreateRequest(ProductType.ASSET, null, null, null).shapeErrors())
                .extracting(FieldErrorResponse::field, FieldErrorResponse::message)
                .containsExactly(tuple("assetSeq", "ASSET 주문에 필수입니다."));
    }

    @Test
    @DisplayName("ASSET 에 PASS 칸이 오면 거절한다 — 무시하지 않는다")
    void assetRejectsPassFields() {
        assertThat(new OrderCreateRequest(ProductType.ASSET, 21, 3, (short) 1).shapeErrors())
                .extracting(FieldErrorResponse::field)
                .containsExactly("artistSeq", "passDurationMonths");
    }

    @Test
    @DisplayName("PASS 에 필수 둘이 빠지고 assetSeq 가 오면 셋 다 보고한다 — 한 번에 고치게")
    void passReportsEveryViolation() {
        assertThat(new OrderCreateRequest(ProductType.PASS, 21, null, null).shapeErrors())
                .extracting(FieldErrorResponse::field, FieldErrorResponse::message)
                .containsExactly(
                        tuple("artistSeq", "PASS 주문에 필수입니다."),
                        tuple("passDurationMonths", "PASS 주문에 필수입니다."),
                        tuple("assetSeq", "PASS 주문에는 보내지 않습니다."));
    }

    @Test
    @DisplayName("productType 이 없으면 여기서는 비어 있다 — 그쪽은 @NotNull 이 잡는다")
    void nullProductTypeIsLeftToBeanValidation() {
        assertThat(new OrderCreateRequest(null, 21, 3, (short) 1).shapeErrors()).isEmpty();
    }
}
