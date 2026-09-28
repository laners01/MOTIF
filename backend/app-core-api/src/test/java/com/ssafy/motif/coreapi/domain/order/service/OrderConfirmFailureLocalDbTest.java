package com.ssafy.motif.coreapi.domain.order.service;

import com.ssafy.motif.common.error.CommonErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.domain.order.OrderErrorCode;
import com.ssafy.motif.coreapi.domain.order.dto.OrderConfirmRequest;
import com.ssafy.motif.coreapi.domain.order.payment.TossApiException;
import com.ssafy.motif.coreapi.domain.order.payment.TossPayment;
import com.ssafy.motif.coreapi.domain.order.payment.TossPaymentClient;
import com.ssafy.motif.coreapi.domain.order.payment.TossUnavailableException;
import com.ssafy.motif.domain.core.artist.Artist;
import com.ssafy.motif.domain.core.artist.ArtistRepository;
import com.ssafy.motif.domain.core.asset.AssetType;
import com.ssafy.motif.domain.core.asset.MotifAsset;
import com.ssafy.motif.domain.core.asset.MotifAssetRepository;
import com.ssafy.motif.domain.core.order.Order;
import com.ssafy.motif.domain.core.order.OrderRepository;
import com.ssafy.motif.domain.core.order.OrderStatus;
import com.ssafy.motif.domain.core.purchase.AssetPurchaseRepository;
import com.ssafy.motif.domain.core.user.User;
import com.ssafy.motif.domain.core.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * §12.2 승인 중 <b>토스 쪽 실패</b>를 실제 커밋·롤백으로 검증한다 (S15P21D104-265).
 *
 * <p><b>{@code @Transactional} 을 붙이지 않는다.</b> 검증 대상이 바로 트랜잭션 경계다 — 토스가
 * 거절하면 PAID 전이가 <b>되돌아가고</b> 그 뒤 FAILED 가 <b>새 트랜잭션으로</b> 남아야 하며,
 * 결과 불명이면 아무것도 남지 않고 PENDING 이어야 한다. 테스트 트랜잭션에 합류시키면 롤백이
 * 일어나지 않아 통과해도 아무것도 증명하지 못한다. 대신 실제로 커밋하고 {@link #tearDown} 에서
 * FK 역순으로 치운다({@code SubscriptionConcurrencyLocalDbTest} 와 같은 이유).
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
class OrderConfirmFailureLocalDbTest {

    private static final String PAYMENT_KEY = "pk-fail";

    @Autowired
    private OrderConfirmService orderConfirmService;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private AssetPurchaseRepository assetPurchaseRepository;
    @Autowired
    private ArtistRepository artistRepository;
    @Autowired
    private MotifAssetRepository motifAssetRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @PersistenceContext
    private EntityManager entityManager;

    @MockitoBean
    private TossPaymentClient tossPaymentClient;

    private final List<Integer> userSeqs = new ArrayList<>();
    private Integer adminUserSeq;
    private Integer fanSeq;
    private Integer companySeq;
    private Integer artistSeq;
    private Integer assetSeq;

    @BeforeEach
    void setUp() {
        inTransaction(() -> {
            adminUserSeq = newUser();
            fanSeq = newUser();
            artistSeq = newArtist();
            assetSeq = onSaleAsset();
        });
    }

    /** 커밋한 행을 직접 치운다 — 롤백이 없다. FK 역순이다. */
    @AfterEach
    void tearDown() {
        inTransaction(() -> {
            execute("delete from motif_core.asset_purchases where user_seq = ?1", fanSeq);
            execute("delete from motif_core.orders where user_seq = ?1", fanSeq);
            execute("delete from motif_core.motif_asset_prices where asset_seq = ?1", assetSeq);
            execute("delete from motif_core.motif_assets where asset_seq = ?1", assetSeq);
            execute("delete from motif_core.artists where artist_seq = ?1", artistSeq);
            execute("delete from motif_core.companies where company_seq = ?1", companySeq);
            for (Integer userSeq : userSeqs) {
                execute("delete from motif_core.users where user_seq = ?1", userSeq);
            }
        });
    }

    @Test
    @DisplayName("토스 거절 — PAID 는 되돌아가고 주문은 FAILED(사유·키 포함), 권한은 없고, 응답은 402 에 토스 코드")
    void rejectionRollsBackPaidAndRecordsFailed() {
        Order order = pendingOrder();
        given(tossPaymentClient.confirm(anyString(), anyString(), any()))
                .willThrow(new TossApiException(403, "REJECT_CARD_COMPANY", "카드사에서 승인을 거절했습니다."));

        BusinessException e = catchThrowableOfType(BusinessException.class, () -> confirm(order));

        assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.PAYMENT_FAILED);
        assertThat(e.getData()).isEqualTo("toss: REJECT_CARD_COMPANY");

        Order failed = reload(order);
        assertThat(failed.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(failed.getPaymentProvider()).isEqualTo("TOSS");
        assertThat(failed.getPaymentKey()).isEqualTo(PAYMENT_KEY);
        assertThat(failed.getFailReason()).contains("REJECT_CARD_COMPANY");
        assertThat(failed.getApprovedDttm()).isNull();
        assertThat(inTransaction(() -> assetPurchaseRepository.findByOrderSeq(order.getOrderSeq()))).isEmpty();
    }

    @Test
    @DisplayName("거절된 주문의 재승인은 409 ORDER_EXPIRED 다 — 되살리지 않고, 토스도 다시 부르지 않는다")
    void failedOrderCannotBeRetried() {
        Order order = pendingOrder();
        given(tossPaymentClient.confirm(anyString(), anyString(), any()))
                .willThrow(new TossApiException(403, "REJECT_CARD_COMPANY", "거절"));
        assertThatThrownBy(() -> confirm(order)).isInstanceOf(BusinessException.class);

        assertThatThrownBy(() -> confirm(order))
                .isInstanceOf(BusinessException.class)
                .extracting(t -> ((BusinessException) t).getErrorCode())
                .isEqualTo(OrderErrorCode.ORDER_EXPIRED);
        verify(tossPaymentClient, times(1)).confirm(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("결과 불명(타임아웃·5xx) — 예외는 그대로 올라가고 주문은 PENDING 그대로다. FAILED 로 기록하지 않는다")
    void unknownOutcomeKeepsOrderPending() {
        Order order = pendingOrder();
        given(tossPaymentClient.confirm(anyString(), anyString(), any()))
                .willThrow(new TossUnavailableException("toss confirm: read timed out", null));

        assertThatThrownBy(() -> confirm(order)).isInstanceOf(TossUnavailableException.class);

        Order pending = reload(order);
        assertThat(pending.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(pending.getPaymentKey()).isNull();
        assertThat(pending.getPaymentProvider()).isNull();
        assertThat(inTransaction(() -> assetPurchaseRepository.findByOrderSeq(order.getOrderSeq()))).isEmpty();
    }

    @Test
    @DisplayName("ALREADY_PROCESSED_PAYMENT 인데 조회 결과가 이 주문과 다르면 거절이다 — FAILED")
    void alreadyProcessedWithMismatchIsRejected() {
        Order order = pendingOrder();
        given(tossPaymentClient.confirm(anyString(), anyString(), any()))
                .willThrow(new TossApiException(400, TossApiException.ALREADY_PROCESSED_PAYMENT, "이미 처리된 결제입니다."));
        given(tossPaymentClient.find(PAYMENT_KEY))
                .willReturn(new TossPayment(PAYMENT_KEY, UUID.randomUUID().toString(), "DONE", new BigDecimal("3900"), "카드"));

        BusinessException e = catchThrowableOfType(BusinessException.class, () -> confirm(order));

        assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.PAYMENT_FAILED);
        assertThat(e.getData()).isEqualTo("toss: ALREADY_PROCESSED_PAYMENT");
        assertThat(reload(order).getStatus()).isEqualTo(OrderStatus.FAILED);
    }

    @Test
    @DisplayName("승인 응답 status 가 DONE 이 아니면 거절로 본다 — FAILED, data 는 toss: STATUS_…")
    void nonDoneStatusIsRejected() {
        Order order = pendingOrder();
        given(tossPaymentClient.confirm(anyString(), anyString(), any()))
                .willReturn(new TossPayment(PAYMENT_KEY, order.getOrderUuid().toString(), "IN_PROGRESS",
                        new BigDecimal("3900"), "카드"));

        BusinessException e = catchThrowableOfType(BusinessException.class, () -> confirm(order));

        assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.PAYMENT_FAILED);
        assertThat(e.getData()).isEqualTo("toss: STATUS_IN_PROGRESS");
        assertThat(reload(order).getStatus()).isEqualTo(OrderStatus.FAILED);
    }

    // --- 픽스처 (전부 inTransaction 안에서) ------------------------------------------

    private void confirm(Order order) {
        orderConfirmService.confirm(fanSeq, order.getOrderUuid(),
                new OrderConfirmRequest(PAYMENT_KEY, order.getOrderUuid().toString(), "3900.00"));
    }

    private Order pendingOrder() {
        return inTransaction(() -> orderRepository.saveAndFlush(Order.forAsset(fanSeq, assetSeq, "KR",
                new BigDecimal("3900.00"), "KRW", Instant.now().plus(30, ChronoUnit.MINUTES))));
    }

    private Order reload(Order order) {
        return inTransaction(() -> orderRepository.findById(order.getOrderSeq()).orElseThrow());
    }

    private void inTransaction(Runnable work) {
        transactionTemplate.executeWithoutResult(status -> work.run());
    }

    private <T> T inTransaction(Supplier<T> work) {
        return transactionTemplate.execute(status -> work.get());
    }

    private Integer newUser() {
        User user = userRepository.saveAndFlush(User.signUp());
        user.applyInitialNickname();
        userSeqs.add(user.getUserSeq());
        return user.getUserSeq();
    }

    private Integer newArtist() {
        User artistUser = userRepository.saveAndFlush(User.createArtist("카리나"));
        userSeqs.add(artistUser.getUserSeq());
        companySeq = newCompany();
        return artistRepository.saveAndFlush(Artist.register(
                artistUser.getUserSeq(), null, companySeq, null, null, adminUserSeq)).getArtistSeq();
    }

    private Integer newCompany() {
        String name = "t-" + UUID.randomUUID().toString().substring(0, 20);
        execute("insert into motif_core.companies (company_name, reg_usr_seq) values (?1, ?2)", name, adminUserSeq);
        return ((Number) entityManager.createNativeQuery(
                        "select company_seq from motif_core.companies where company_name = ?1")
                .setParameter(1, name)
                .getSingleResult()).intValue();
    }

    private Integer onSaleAsset() {
        String uuid = UUID.randomUUID().toString();
        MotifAsset asset = motifAssetRepository.saveAndFlush(MotifAsset.register(artistSeq,
                "에셋 " + uuid.substring(0, 8), "소개 문구",
                "assets/" + uuid + ".mp4", "assets/" + uuid + "/thumb.jpg", 9_800, AssetType.NORMAL));
        execute("""
                insert into motif_core.motif_asset_prices
                       (asset_seq, country_seq, amount, currency, is_active, reg_usr_seq)
                values (?1, (select country_seq from motif_core.countries where country_code = 'KR'),
                        3900.00, 'KRW', true, ?2)
                """, asset.getAssetSeq(), adminUserSeq);
        return asset.getAssetSeq();
    }

    private void execute(String sql, Object... params) {
        var query = entityManager.createNativeQuery(sql);
        for (int i = 0; i < params.length; i++) {
            query.setParameter(i + 1, params[i]);
        }
        query.executeUpdate();
    }
}
