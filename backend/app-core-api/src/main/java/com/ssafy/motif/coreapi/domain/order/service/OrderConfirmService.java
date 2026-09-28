package com.ssafy.motif.coreapi.domain.order.service;

import com.ssafy.motif.common.error.CommonErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.domain.order.dto.OrderConfirmRequest;
import com.ssafy.motif.coreapi.domain.order.dto.OrderConfirmResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 결제 승인 (API 명세 §12.2) — 파사드.
 *
 * <p><b>{@code @Transactional} 이 없다.</b> 승인 본체는 {@link OrderConfirmTransaction#confirm}
 * 이고, 토스가 거절하면 그 트랜잭션이 <b>되돌아간 뒤</b>에 {@code FAILED} 를 새 트랜잭션으로
 * 남겨야 한다. 이 두 트랜잭션의 경계를 여기서 만든다 — 파사드가 트랜잭션을 열면 둘이 하나로
 * 합쳐져 PAID 롤백과 FAILED 기록이 함께 사라진다.
 *
 * <p>거절 응답은 402 {@code PAYMENT_FAILED} 이고 {@code data} 에 토스 코드({@code toss: CODE})를
 * 싣는다(§12.2). 실패한 주문은 되살리지 않는다 — 재시도는 §12.1 부터 새 주문이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderConfirmService {

    private final OrderConfirmTransaction transaction;

    /**
     * @throws BusinessException 400 {@code INVALID_PARAMETER}/{@code AMOUNT_MISMATCH} ·
     *                           402 {@code PAYMENT_FAILED} · 404 {@code ORDER_NOT_FOUND} ·
     *                           409 {@code ORDER_EXPIRED}/{@code ORDER_ALREADY_PAID}/재검증 코드
     */
    public OrderConfirmResponse confirm(Integer userSeq, UUID orderUuid, OrderConfirmRequest request) {
        try {
            return transaction.confirm(userSeq, orderUuid, request);
        } catch (PaymentRejectedException rejected) {
            // 여기 도달했으면 승인 트랜잭션은 이미 롤백됐다 — PAID 가 되돌아간 뒤에 FAILED 를 쓴다
            transaction.recordRejection(rejected);
            log.warn("토스 승인 거절 orderUuid={} code={}", orderUuid, rejected.getCode());
            throw new BusinessException(CommonErrorCode.PAYMENT_FAILED,
                    "toss " + rejected.getCode() + ": " + rejected.getTossMessage(),
                    "toss: " + rejected.getCode());
        }
    }
}
