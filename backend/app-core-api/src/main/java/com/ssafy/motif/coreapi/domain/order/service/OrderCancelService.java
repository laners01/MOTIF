package com.ssafy.motif.coreapi.domain.order.service;

import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.domain.order.OrderErrorCode;
import com.ssafy.motif.coreapi.domain.order.dto.OrderCancelResponse;
import com.ssafy.motif.domain.core.order.Order;
import com.ssafy.motif.domain.core.order.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 승인 전 취소 (API 명세 §12.3) — {@code PENDING → CANCELED}.
 *
 * <p>사용자가 결제 위젯에서 이탈했을 때 클라이언트가 부른다. 부르지 않고 두어도 30분 뒤
 * 배치가 {@code EXPIRED} 로 바꾸므로 <b>기능상 차이는 없고 상태 표기만 다르다</b>.
 *
 * <p><b>판정은 조건부 UPDATE 하나다.</b> {@code status='PENDING'} 조건이 걸린 UPDATE 가 0행이면
 * 이미 결제됐거나 끝난 주문이라 409 다 — 상태를 먼저 읽고 갈라 쓰면 읽는 시점과 쓰는 시점
 * 사이에 승인이 끼어들 수 있다({@code SubscriptionCommandService} 와 같은 원칙). 그래서
 * 회원 잠금도 걸지 않는다.
 *
 * <p>만료 시각은 보지 않는다(기본안) — 기간이 지났어도 배치 전이라 {@code PENDING} 이면
 * 취소된다. 취소된 주문은 되살리지 않는다 — 다시 사려면 §12.1 부터 새 주문이다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class OrderCancelService {

    private final OrderRepository orderRepository;

    /**
     * @throws BusinessException 404 {@code ORDER_NOT_FOUND} — 없음·타인 주문 ·
     *                           409 {@code ORDER_NOT_CANCELABLE} — {@code PENDING} 이 아님
     */
    public OrderCancelResponse cancel(Integer userSeq, UUID orderUuid) {
        Order order = orderRepository.findByOrderUuidAndUserSeq(orderUuid, userSeq)
                .orElseThrow(() -> new BusinessException(
                        OrderErrorCode.ORDER_NOT_FOUND, "orderUuid: " + orderUuid));

        if (orderRepository.markCanceled(order.getOrderSeq()) == 0) {
            // 읽었을 때의 상태를 로그에 남긴다 — UPDATE 시점에는 더 바뀌어 있을 수 있다
            throw new BusinessException(OrderErrorCode.ORDER_NOT_CANCELABLE,
                    "orderUuid: " + orderUuid + ", status(읽은 시점): " + order.getStatus());
        }
        return OrderCancelResponse.canceled(orderUuid);
    }
}
