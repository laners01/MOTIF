package com.ssafy.motif.domain.core.order;

/**
 * {@code orders.product_type} — DDL CHECK({@code ck_orders_product_type})와 값이 일치해야 한다.
 *
 * <p>값에 따라 채워지는 칸이 다르다({@code ck_orders_product_shape}) — {@code ASSET} 은
 * {@code asset_seq} 만, {@code PASS} 는 {@code artist_seq}·{@code pass_duration_months} 만.
 * 그 모양은 {@link Order} 의 정적 팩토리가 지킨다.
 */
public enum ProductType {
    ASSET, PASS
}
