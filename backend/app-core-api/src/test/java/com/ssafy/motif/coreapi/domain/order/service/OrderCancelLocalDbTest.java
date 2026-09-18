package com.ssafy.motif.coreapi.domain.order.service;

import com.ssafy.motif.common.error.ErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.domain.order.OrderErrorCode;
import com.ssafy.motif.coreapi.domain.order.dto.OrderCancelResponse;
import com.ssafy.motif.domain.core.artist.Artist;
import com.ssafy.motif.domain.core.artist.ArtistRepository;
import com.ssafy.motif.domain.core.order.Order;
import com.ssafy.motif.domain.core.order.OrderRepository;
import com.ssafy.motif.domain.core.order.OrderStatus;
import com.ssafy.motif.domain.core.user.User;
import com.ssafy.motif.domain.core.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * §12.3 승인 전 취소를 실제 DB 에 대고 검증한다 (S15P21D104-265).
 *
 * <p>고정하는 계약은 둘이다 — <b>{@code PENDING} 만 취소된다</b>(다른 상태는 전부 409, 이미 취소된
 * 것도), 그리고 <b>만료 시각은 보지 않는다</b>(기간이 지났어도 배치 전이면 취소된다). 판정이 조건부
 * UPDATE 안에 있어 DB 없이는 확인할 수 없다.
 *
 * <p>주문은 PASS 형태로 만든다 — 에셋 행이 필요 없어 픽스처가 가장 짧다. 취소는 상품 종류를 보지
 * 않는다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class OrderCancelLocalDbTest {

    @Autowired
    private OrderCancelService orderCancelService;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private ArtistRepository artistRepository;
    @Autowired
    private UserRepository userRepository;
    @PersistenceContext
    private EntityManager entityManager;

    private Integer adminUserSeq;
    private Integer fanSeq;
    private Integer artistSeq;

    @BeforeEach
    void setUp() {
        adminUserSeq = newUser().getUserSeq();
        fanSeq = newUser().getUserSeq();
        artistSeq = newArtist();
    }

    @Test
    @DisplayName("PENDING 주문은 CANCELED 가 되고 응답은 uuid 와 CANCELED 다 — 결제 칸은 그대로 비어 있다")
    void pendingOrderIsCanceled() {
        Order order = pendingOrder();

        OrderCancelResponse response = orderCancelService.cancel(fanSeq, order.getOrderUuid());

        assertThat(response.orderUuid()).isEqualTo(order.getOrderUuid());
        assertThat(response.status()).isEqualTo(OrderStatus.CANCELED);

        Order canceled = reload(order);
        assertThat(canceled.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(canceled.getModDttm()).isNotNull();
        assertThat(canceled.getPaymentProvider()).isNull();
        assertThat(canceled.getPaymentKey()).isNull();
        assertThat(canceled.getApprovedDttm()).isNull();
    }

    @Test
    @DisplayName("기간이 지났지만 아직 PENDING 인 주문도 취소된다 — 만료 시각을 보지 않는다")
    void expiredButPendingOrderIsStillCanceled() {
        Long orderSeq = expiredPendingOrder();
        UUID uuid = orderRepository.findById(orderSeq).orElseThrow().getOrderUuid();

        assertThat(orderCancelService.cancel(fanSeq, uuid).status()).isEqualTo(OrderStatus.CANCELED);
        assertThat(orderRepository.findById(orderSeq).orElseThrow().getStatus()).isEqualTo(OrderStatus.CANCELED);
    }

    @Test
    @DisplayName("없는 주문·남의 주문은 404 ORDER_NOT_FOUND 다 — 둘을 가르지 않는다")
    void unknownOrForeignOrderIsNotFound() {
        Order foreign = orderRepository.saveAndFlush(Order.forPass(newUser().getUserSeq(), artistSeq, (short) 1,
                "KR", new BigDecimal("9900.00"), "KRW", Instant.now().plus(30, ChronoUnit.MINUTES)));

        assertFails(() -> orderCancelService.cancel(fanSeq, UUID.randomUUID()), OrderErrorCode.ORDER_NOT_FOUND);
        assertFails(() -> orderCancelService.cancel(fanSeq, foreign.getOrderUuid()), OrderErrorCode.ORDER_NOT_FOUND);
        assertThat(reload(foreign).getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("이미 취소된 주문의 재취소는 409 ORDER_NOT_CANCELABLE 다")
    void cancelingTwiceIsNotCancelable() {
        Order order = pendingOrder();
        orderCancelService.cancel(fanSeq, order.getOrderUuid());

        assertFails(() -> orderCancelService.cancel(fanSeq, order.getOrderUuid()), OrderErrorCode.ORDER_NOT_CANCELABLE);
        assertThat(reload(order).getStatus()).isEqualTo(OrderStatus.CANCELED);
    }

    @Test
    @DisplayName("결제된 주문은 취소할 수 없다 — 409, 상태는 PAID 그대로 (환불은 어드민 범위)")
    void paidOrderIsNotCancelable() {
        Order order = pendingOrder();
        markPaid(order);

        assertFails(() -> orderCancelService.cancel(fanSeq, order.getOrderUuid()), OrderErrorCode.ORDER_NOT_CANCELABLE);
        assertThat(reload(order).getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("만료·실패 처리된 주문도 409 다 — PENDING 이 아닌 상태는 전부 같은 코드다")
    void expiredAndFailedOrdersAreNotCancelable() {
        Order expired = pendingOrder();
        setStatus(expired, "EXPIRED");
        Order failed = pendingOrder();
        markFailed(failed);

        assertFails(() -> orderCancelService.cancel(fanSeq, expired.getOrderUuid()), OrderErrorCode.ORDER_NOT_CANCELABLE);
        assertFails(() -> orderCancelService.cancel(fanSeq, failed.getOrderUuid()), OrderErrorCode.ORDER_NOT_CANCELABLE);
        assertThat(reload(expired).getStatus()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(reload(failed).getStatus()).isEqualTo(OrderStatus.FAILED);
    }

    // --- 픽스처 -------------------------------------------------------------

    private void assertFails(ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }

    private Order pendingOrder() {
        return orderRepository.saveAndFlush(Order.forPass(fanSeq, artistSeq, (short) 1, "KR",
                new BigDecimal("9900.00"), "KRW", Instant.now().plus(30, ChronoUnit.MINUTES)));
    }

    private Order reload(Order order) {
        entityManager.flush();
        entityManager.clear();
        return orderRepository.findById(order.getOrderSeq()).orElseThrow();
    }

    /** 기간이 지난 PENDING — JPA 는 reg_dttm 을 지금으로 찍으므로 네이티브로 과거에 넣는다. */
    private Long expiredPendingOrder() {
        UUID uuid = UUID.randomUUID();
        entityManager.createNativeQuery("""
                        insert into motif_core.orders
                               (order_uuid, user_seq, product_type, artist_seq, country_code, pass_duration_months,
                                amount, currency, status, expires_dttm, reg_dttm)
                        values (cast(:uuid as uuid), :userSeq, 'PASS', :artistSeq, 'KR', 1,
                                9900.00, 'KRW', 'PENDING', now() - interval '1 hour', now() - interval '2 hours')
                        """)
                .setParameter("uuid", uuid.toString())
                .setParameter("userSeq", fanSeq)
                .setParameter("artistSeq", artistSeq)
                .executeUpdate();
        entityManager.clear();
        return orderRepository.findByOrderUuid(uuid).orElseThrow().getOrderSeq();
    }

    private void setStatus(Order order, String status) {
        entityManager.createNativeQuery("update motif_core.orders set status = :status where order_seq = :seq")
                .setParameter("status", status)
                .setParameter("seq", order.getOrderSeq())
                .executeUpdate();
        entityManager.clear();
    }

    /** PAID 의 상태 모양(ck_orders_status_shape) — 제공자·키·승인 시각이 함께 있어야 한다. */
    private void markPaid(Order order) {
        entityManager.createNativeQuery("""
                        update motif_core.orders
                           set status = 'PAID', payment_provider = 'TEST', payment_key = :key, approved_dttm = now()
                         where order_seq = :seq
                        """)
                .setParameter("key", "pk-" + order.getOrderUuid())
                .setParameter("seq", order.getOrderSeq())
                .executeUpdate();
        entityManager.clear();
    }

    /** FAILED 의 상태 모양 — 제공자·사유가 함께 있어야 한다. */
    private void markFailed(Order order) {
        entityManager.createNativeQuery("""
                        update motif_core.orders
                           set status = 'FAILED', payment_provider = 'TEST', fail_reason = 'test: REJECT_CARD_COMPANY'
                         where order_seq = :seq
                        """)
                .setParameter("seq", order.getOrderSeq())
                .executeUpdate();
        entityManager.clear();
    }

    private User newUser() {
        User user = userRepository.saveAndFlush(User.signUp());
        user.applyInitialNickname();
        return user;
    }

    private Integer newArtist() {
        User artistUser = userRepository.saveAndFlush(User.createArtist("카리나"));
        return artistRepository.saveAndFlush(Artist.register(
                artistUser.getUserSeq(), null, newCompany(), null, null, adminUserSeq)).getArtistSeq();
    }

    private Integer newCompany() {
        String name = "t-" + UUID.randomUUID().toString().substring(0, 20);
        entityManager.createNativeQuery(
                        "insert into motif_core.companies (company_name, reg_usr_seq) values (:name, :regUsrSeq)")
                .setParameter("name", name)
                .setParameter("regUsrSeq", adminUserSeq)
                .executeUpdate();
        return ((Number) entityManager.createNativeQuery(
                        "select company_seq from motif_core.companies where company_name = :name")
                .setParameter("name", name)
                .getSingleResult()).intValue();
    }
}
