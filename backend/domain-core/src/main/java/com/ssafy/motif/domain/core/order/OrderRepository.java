package com.ssafy.motif.domain.core.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * 외부 식별자로 주문을 집는다 — {@code uq_orders_order_uuid} 가 유일성을 보장한다.
     *
     * <p>클라이언트와 토스는 {@code orderUuid} 만 안다({@link Order} javadoc). 승인·취소
     * (§12.2·§12.3)는 여기에 <b>본인 조건</b>을 더해 써야 한다 — 타인 주문은 404 다.
     */
    Optional<Order> findByOrderUuid(UUID orderUuid);
}
