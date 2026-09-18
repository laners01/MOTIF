package com.ssafy.motif.domain.core.order;

/**
 * {@code orders.status} — DDL CHECK({@code ck_orders_status})와 값이 일치해야 한다.
 *
 * <p>허용 전이는 {@code PENDING → PAID / FAILED / EXPIRED / CANCELED}, {@code PAID → REFUNDED}
 * 다(DB SPEC v0.15 §33). {@code FAILED}·{@code EXPIRED}·{@code CANCELED}·{@code REFUNDED} 는
 * 종료 상태라 되살리지 않는다 — 재시도는 새 주문이다.
 *
 * <p>상태마다 함께 있어야 하는 칸이 정해져 있다({@code ck_orders_status_shape}). 예를 들어
 * {@code PENDING} 은 {@code payment_key}·{@code approved_dttm}·{@code fail_reason} 이 전부
 * NULL 이어야 한다.
 */
public enum OrderStatus {
    PENDING, PAID, FAILED, EXPIRED, CANCELED, REFUNDED
}
