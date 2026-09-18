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
 * <p>승인·취소(§12.2·§12.3)의 코드({@code AMOUNT_MISMATCH}·{@code ORDER_NOT_FOUND}·
 * {@code ORDER_EXPIRED} …)는 그 티켓이 여기에 덧붙인다. {@code PAYMENT_FAILED} 는
 * {@code CommonErrorCode} 가 이미 갖고 있다.
 */
@Getter
@RequiredArgsConstructor
public enum OrderErrorCode implements ErrorCode {

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
    PASS_ALREADY_ACTIVE(409, "PASS_ALREADY_ACTIVE", "이미 이용 중인 패스입니다.");

    private final int status;
    private final String code;
    private final String message;
}
