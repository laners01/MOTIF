package com.ssafy.motif.coreapi.domain.order;

import com.ssafy.motif.common.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 주문 도메인 에러 코드 (API 명세 §15 — 12.1).
 *
 * <p>넷 다 409 다 — 요청 형식은 맞는데 <b>지금 상태</b>가 주문을 허락하지 않는 경우다.
 * 대상이 없는 것은 도메인 소유자의 코드({@code AssetErrorCode.ASSET_NOT_FOUND}·
 * {@code ArtistErrorCode.ARTIST_NOT_FOUND})를 그대로 쓰고 여기 다시 만들지 않는다
 * ({@code code} 전역 유일 — README §3).
 *
 * <p>{@code PAYMENT_FAILED}(402) 는 {@code CommonErrorCode} 가 갖고 있다 — 명세 §1.4 가 도메인
 * 공용 확장으로 묶은 코드라 여기 다시 만들지 않는다.
 */
@Getter
@RequiredArgsConstructor
public enum OrderErrorCode implements ErrorCode {

    // --- 12.1 · 12.2 재검증 ---------------------------------------------------------

    /** {@code is_active=FALSE} 또는 해당 국가의 활성 가격 없음 — ASSET·PASS 공통. */
    PRODUCT_NOT_ON_SALE(409, "PRODUCT_NOT_ON_SALE", "판매 중인 상품이 아닙니다."),

    /** ACTIVE 개별 구매를 이미 보유 (ASSET). DB 가 (회원, 에셋) ACTIVE 구매를 하나로 막는다. */
    ALREADY_OWNED(409, "ALREADY_OWNED", "이미 보유한 에셋입니다."),

    /**
     * 유효 패스로 이미 이용 가능한 에셋 (ASSET). {@code PREMIUM} 은 패스로 열리지 않으므로
     * 이 코드가 나오지 않는다(§11 권한 표).
     */
    COVERED_BY_PASS(409, "COVERED_BY_PASS", "패스로 이용 중인 에셋입니다."),

    /** 유효 패스 보유 중 (PASS). 만료·환불 뒤의 재구매는 막지 않는다. */
    PASS_ALREADY_ACTIVE(409, "PASS_ALREADY_ACTIVE", "이미 이용 중인 패스입니다."),

    // --- 12.2 승인 --------------------------------------------------------------------

    /** 콜백 {@code amount} ≠ 주문 스냅샷 — 토스 승인을 부르기 <b>전에</b> 막는다 (§12.2 1단계). */
    AMOUNT_MISMATCH(400, "AMOUNT_MISMATCH", "결제 금액이 주문과 다릅니다."),

    /** 주문 없음·타인 주문 (§12.2·§12.3). 둘을 가르지 않는다 — 존재 은닉(§1.8). */
    ORDER_NOT_FOUND(404, "ORDER_NOT_FOUND", "존재하지 않는 주문입니다."),

    /**
     * 더 이상 결제할 수 없는 주문 (§12.2) — 30분 만료, 또는 종료 상태
     * ({@code EXPIRED}·{@code CANCELED}·{@code FAILED})의 승인 시도. 재시도는 새 주문이다.
     */
    ORDER_EXPIRED(409, "ORDER_EXPIRED", "만료된 주문입니다."),

    /** 이미 {@code PAID}(·{@code REFUNDED}) 인 주문의 중복 승인 (§12.2). */
    ORDER_ALREADY_PAID(409, "ORDER_ALREADY_PAID", "이미 결제된 주문입니다."),

    // --- 12.3 취소 --------------------------------------------------------------------

    /** {@code PENDING} 이 아닌 주문의 취소 시도 (§12.3) — 결제됨·만료·취소·실패·환불 전부 같은 코드다. */
    ORDER_NOT_CANCELABLE(409, "ORDER_NOT_CANCELABLE", "취소할 수 없는 주문입니다.");

    private final int status;
    private final String code;
    private final String message;
}
