package com.ssafy.motif.coreapi.domain.order.service;

import com.ssafy.motif.common.error.ErrorCode;
import com.ssafy.motif.common.exception.BusinessException;

/**
 * 승인 도중 주문을 {@code EXPIRED} 로 바꾼 <b>뒤에</b> 던지는 거절 (API 명세 §12.2 2단계).
 *
 * <p>보통의 {@link BusinessException} 은 트랜잭션을 되돌리는데, 이 거절은 방금 한
 * {@code PENDING → EXPIRED} 전이가 <b>남아야</b> 한다 — 그래서
 * {@link OrderConfirmTransaction} 이 {@code noRollbackFor} 로 이 타입만 커밋시킨다.
 * 응답은 다른 409 와 같다(전역 핸들러는 부모 타입으로 잡는다).
 *
 * <p>이 예외를 던지기 전에 쓴 것이 {@code EXPIRED} 전이 하나뿐이어야 한다. PAID 전이 뒤에는
 * 절대 던지지 않는다 — 그쪽은 되돌려야 하는 실패다.
 */
class OrderRejectedException extends BusinessException {

    OrderRejectedException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
