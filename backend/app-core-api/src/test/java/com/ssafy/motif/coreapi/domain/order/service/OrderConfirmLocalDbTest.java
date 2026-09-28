package com.ssafy.motif.coreapi.domain.order.service;

import com.ssafy.motif.common.error.CommonErrorCode;
import com.ssafy.motif.common.error.ErrorCode;
import com.ssafy.motif.common.error.FieldErrorResponse;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.domain.order.OrderErrorCode;
import com.ssafy.motif.coreapi.domain.order.dto.OrderConfirmRequest;
import com.ssafy.motif.coreapi.domain.order.dto.OrderConfirmResponse;
import com.ssafy.motif.coreapi.domain.order.payment.TossApiException;
import com.ssafy.motif.coreapi.domain.order.payment.TossPayment;
import com.ssafy.motif.coreapi.domain.order.payment.TossPaymentClient;
import com.ssafy.motif.domain.core.artist.Artist;
import com.ssafy.motif.domain.core.artist.ArtistRepository;
import com.ssafy.motif.domain.core.asset.AssetType;
import com.ssafy.motif.domain.core.asset.MotifAsset;
import com.ssafy.motif.domain.core.asset.MotifAssetRepository;
import com.ssafy.motif.domain.core.order.Order;
import com.ssafy.motif.domain.core.order.OrderRepository;
import com.ssafy.motif.domain.core.order.OrderStatus;
import com.ssafy.motif.domain.core.order.ProductType;
import com.ssafy.motif.domain.core.pass.MotifPass;
import com.ssafy.motif.domain.core.pass.MotifPassRepository;
import com.ssafy.motif.domain.core.pass.PassStatus;
import com.ssafy.motif.domain.core.purchase.AssetPurchase;
import com.ssafy.motif.domain.core.purchase.AssetPurchaseRepository;
import com.ssafy.motif.domain.core.purchase.AssetPurchaseStatus;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * §12.2 결제 승인을 실제 DB 에 대고 검증한다 — 토스는 가짜 빈이다 (S15P21D104-265).
 *
 * <p><b>커밋되는 경로만 여기 있다.</b> 승인 성공과, 토스를 부르기 전에 끝나는 400·404·409 다.
 * 테스트 트랜잭션 안에서 서비스 트랜잭션이 합류하므로 롤백이 실제로 일어나야 하는 경로
 * (토스 거절 → FAILED 기록, 결과 불명 → PENDING 유지)는 검증할 수 없다 — 그쪽은
 * {@link OrderConfirmFailureLocalDbTest} 가 실제 커밋으로 돈다.
 *
 * <p>여기서 고정하는 계약: 승인 전 대조가 <b>토스를 부르지 않고</b> 끝나는가, 재검증 실패가
 * 주문을 {@code EXPIRED} 로 바꾸는가({@code noRollbackFor}), 권한 행이 같은 트랜잭션에서
 * 생기는가, PASS 재구매가 <b>같은 행</b>을 되살리는가.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class OrderConfirmLocalDbTest {

    private static final String PAYMENT_KEY = "pk-test";

    @Autowired
    private OrderConfirmService orderConfirmService;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private AssetPurchaseRepository assetPurchaseRepository;
    @Autowired
    private MotifPassRepository motifPassRepository;
    @Autowired
    private ArtistRepository artistRepository;
    @Autowired
    private MotifAssetRepository motifAssetRepository;
    @Autowired
    private UserRepository userRepository;
    @PersistenceContext
    private EntityManager entityManager;

    @MockitoBean
    private TossPaymentClient tossPaymentClient;

    private Integer adminUserSeq;
    private Integer fanSeq;
    private Integer artistSeq;

    @BeforeEach
    void setUp() {
        adminUserSeq = newUser().getUserSeq();
        fanSeq = newUser().getUserSeq();
        artistSeq = newArtist();
    }

    // --- 성공 ------------------------------------------------------------------

    @Test
    @DisplayName("ASSET 승인 — PAID 전이 + asset_purchases(ACTIVE) 가 같은 트랜잭션에서 생기고 응답은 asset 만 싣는다")
    void assetConfirmGrantsPurchase() {
        MotifAsset asset = onSaleAsset(AssetType.NORMAL);
        Order order = pendingAssetOrder(asset.getAssetSeq(), "3900.00");
        tossApproves(order, "3900");

        OrderConfirmResponse response = confirm(order, "3900.00");

        assertThat(response.status()).isEqualTo(OrderStatus.PAID);
        assertThat(response.productType()).isEqualTo(ProductType.ASSET);
        assertThat(response.asset().assetSeq()).isEqualTo(asset.getAssetSeq());
        assertThat(response.pass()).isNull();

        Order paid = reload(order);
        assertThat(paid.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(paid.getPaymentProvider()).isEqualTo("TOSS");
        assertThat(paid.getPaymentKey()).isEqualTo(PAYMENT_KEY);
        assertThat(paid.getApprovedDttm()).isNotNull();
        assertThat(paid.getFailReason()).isNull();

        AssetPurchase purchase = assetPurchaseRepository.findByOrderSeq(order.getOrderSeq()).orElseThrow();
        assertThat(purchase.getUserSeq()).isEqualTo(fanSeq);
        assertThat(purchase.getAssetSeq()).isEqualTo(asset.getAssetSeq());
        assertThat(purchase.getStatus()).isEqualTo(AssetPurchaseStatus.ACTIVE);

        verify(tossPaymentClient).confirm(eq(PAYMENT_KEY), eq(order.getOrderUuid().toString()),
                argThat(amount -> amount.compareTo(new BigDecimal("3900.00")) == 0));
    }

    @Test
    @DisplayName("콜백 amount 가 소수부 없는 3900 이어도 스냅샷 3900.00 과 같다 — compareTo")
    void integerCallbackAmountMatchesSnapshot() {
        MotifAsset asset = onSaleAsset(AssetType.NORMAL);
        Order order = pendingAssetOrder(asset.getAssetSeq(), "3900.00");
        tossApproves(order, "3900");

        assertThat(confirm(order, "3900").status()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("PASS 최초 승인 — motif_passes 행이 생기고 만료는 KST 달력으로 개월을 더한 값이다")
    void passConfirmIssuesPass() {
        passPrice((short) 1, "9900.00");
        Order order = pendingPassOrder((short) 1, "9900.00");
        tossApproves(order, "9900");

        OrderConfirmResponse response = confirm(order, "9900.00");

        assertThat(response.productType()).isEqualTo(ProductType.PASS);
        assertThat(response.asset()).isNull();

        MotifPass pass = motifPassRepository.findByUserSeqAndArtistSeq(fanSeq, artistSeq).orElseThrow();
        assertThat(pass.getStatus()).isEqualTo(PassStatus.ACTIVE);
        assertThat(pass.getOrderSeq()).isEqualTo(order.getOrderSeq());
        assertThat(pass.getDurationMonths()).isEqualTo((short) 1);
        assertThat(pass.getCanceledDttm()).isNull();
        Instant expectedExpiry = ZonedDateTime.ofInstant(pass.getStartedDttm(), OrderConfirmTransaction.PASS_ZONE)
                .plusMonths(1).toInstant();
        assertThat(pass.getExpiresDttm()).isCloseTo(expectedExpiry, within(1, ChronoUnit.SECONDS));
        assertThat(response.pass().expiresDttm()).isCloseTo(pass.getExpiresDttm(), within(1, ChronoUnit.MILLIS));
        assertThat(reload(order).getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("PASS 재구매 — 새 행이 아니라 만료된 같은 행을 되살린다 (order_seq·기간·취소 의사 갱신)")
    void passRepurchaseReactivatesSameRow() {
        passPrice((short) 3, "27900.00");
        Integer oldPassSeq = expiredPass(Instant.now().minus(10, ChronoUnit.DAYS));
        Order order = pendingPassOrder((short) 3, "27900.00");
        tossApproves(order, "27900");

        confirm(order, "27900.00");

        MotifPass pass = motifPassRepository.findByUserSeqAndArtistSeq(fanSeq, artistSeq).orElseThrow();
        assertThat(pass.getPassSeq()).isEqualTo(oldPassSeq);
        assertThat(pass.getStatus()).isEqualTo(PassStatus.ACTIVE);
        assertThat(pass.getOrderSeq()).isEqualTo(order.getOrderSeq());
        assertThat(pass.getDurationMonths()).isEqualTo((short) 3);
        assertThat(pass.getCanceledDttm()).isNull();
        assertThat(pass.getExpiresDttm()).isAfter(Instant.now().plus(80, ChronoUnit.DAYS));
        assertThat(motifPassRepository.findByUserSeqOrderByExpiresDttmDescPassSeqDesc(fanSeq)).hasSize(1);
    }

    @Test
    @DisplayName("토스가 ALREADY_PROCESSED_PAYMENT 를 주면 결제 조회로 확인한 뒤 승인으로 간주한다 — 복구 경로")
    void alreadyProcessedIsRecoveredByLookup() {
        MotifAsset asset = onSaleAsset(AssetType.NORMAL);
        Order order = pendingAssetOrder(asset.getAssetSeq(), "3900.00");
        given(tossPaymentClient.confirm(anyString(), anyString(), any()))
                .willThrow(new TossApiException(400, TossApiException.ALREADY_PROCESSED_PAYMENT, "이미 처리된 결제입니다."));
        given(tossPaymentClient.find(PAYMENT_KEY)).willReturn(donePayment(order, "3900"));

        OrderConfirmResponse response = confirm(order, "3900.00");

        assertThat(response.status()).isEqualTo(OrderStatus.PAID);
        assertThat(reload(order).getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(assetPurchaseRepository.findByOrderSeq(order.getOrderSeq())).isPresent();
    }

    // --- 토스를 부르기 전에 끝나는 실패 --------------------------------------------

    @Test
    @DisplayName("없는 주문·남의 주문은 404 ORDER_NOT_FOUND 다 — 토스를 부르지 않는다")
    void unknownOrForeignOrderIsNotFound() {
        MotifAsset asset = onSaleAsset(AssetType.NORMAL);
        Order foreign = orderRepository.saveAndFlush(Order.forAsset(newUser().getUserSeq(), asset.getAssetSeq(),
                "KR", new BigDecimal("3900.00"), "KRW", Instant.now().plus(30, ChronoUnit.MINUTES)));

        assertFails(() -> confirm(UUID.randomUUID(), UUID.randomUUID().toString(), "3900.00"),
                OrderErrorCode.ORDER_NOT_FOUND);
        assertFails(() -> confirm(foreign, "3900.00"), OrderErrorCode.ORDER_NOT_FOUND);
        verifyNoInteractions(tossPaymentClient);
    }

    @Test
    @DisplayName("본문 orderId 가 경로와 다르면 400 INVALID_PARAMETER 이고 data 에 필드 오류가 실린다")
    void orderIdMismatchIsInvalidParameter() {
        MotifAsset asset = onSaleAsset(AssetType.NORMAL);
        Order order = pendingAssetOrder(asset.getAssetSeq(), "3900.00");

        BusinessException e = catchThrowableOfType(BusinessException.class,
                () -> confirm(order.getOrderUuid(), UUID.randomUUID().toString(), "3900.00"));

        assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_PARAMETER);
        assertThat((List<?>) e.getData()).singleElement()
                .extracting(f -> ((FieldErrorResponse) f).field()).isEqualTo("orderId");
        verifyNoInteractions(tossPaymentClient);
    }

    @Test
    @DisplayName("amount 가 숫자가 아니면 400 INVALID_PARAMETER 다")
    void nonNumericAmountIsInvalidParameter() {
        MotifAsset asset = onSaleAsset(AssetType.NORMAL);
        Order order = pendingAssetOrder(asset.getAssetSeq(), "3900.00");

        assertFails(() -> confirm(order, "삼천구백"), CommonErrorCode.INVALID_PARAMETER);
        verifyNoInteractions(tossPaymentClient);
    }

    @Test
    @DisplayName("amount 가 스냅샷과 다르면 400 AMOUNT_MISMATCH — 토스를 부르지 않고 주문은 PENDING 그대로다")
    void amountMismatchBlocksBeforeToss() {
        MotifAsset asset = onSaleAsset(AssetType.NORMAL);
        Order order = pendingAssetOrder(asset.getAssetSeq(), "3900.00");

        assertFails(() -> confirm(order, "3901"), OrderErrorCode.AMOUNT_MISMATCH);

        assertThat(reload(order).getStatus()).isEqualTo(OrderStatus.PENDING);
        verifyNoInteractions(tossPaymentClient);
    }

    @Test
    @DisplayName("같은 주문을 두 번 승인하면 두 번째는 409 ORDER_ALREADY_PAID 다 — 토스는 한 번만 불린다")
    void secondConfirmIsAlreadyPaid() {
        MotifAsset asset = onSaleAsset(AssetType.NORMAL);
        Order order = pendingAssetOrder(asset.getAssetSeq(), "3900.00");
        tossApproves(order, "3900");
        confirm(order, "3900.00");

        assertFails(() -> confirm(order, "3900.00"), OrderErrorCode.ORDER_ALREADY_PAID);
        verify(tossPaymentClient).confirm(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("30분이 지난 PENDING 주문은 409 ORDER_EXPIRED 이고 주문이 EXPIRED 로 바뀐다 — 그 전이는 남는다")
    void expiredPendingOrderIsRejectedAndMarkedExpired() {
        MotifAsset asset = onSaleAsset(AssetType.NORMAL);
        Long orderSeq = expiredPendingAssetOrder(asset.getAssetSeq());
        Order order = orderRepository.findById(orderSeq).orElseThrow();

        assertFails(() -> confirm(order, "3900.00"), OrderErrorCode.ORDER_EXPIRED);

        assertThat(reload(order).getStatus()).isEqualTo(OrderStatus.EXPIRED);
        verifyNoInteractions(tossPaymentClient);
    }

    @Test
    @DisplayName("취소된 주문의 승인 시도는 409 ORDER_EXPIRED 다 — 종료 상태는 되살리지 않는다")
    void canceledOrderCannotBeConfirmed() {
        MotifAsset asset = onSaleAsset(AssetType.NORMAL);
        Order order = pendingAssetOrder(asset.getAssetSeq(), "3900.00");
        setStatus(order, "CANCELED");

        assertFails(() -> confirm(order, "3900.00"), OrderErrorCode.ORDER_EXPIRED);
        assertThat(reload(order).getStatus()).isEqualTo(OrderStatus.CANCELED);
        verifyNoInteractions(tossPaymentClient);
    }

    // --- 재검증 실패 — EXPIRED 로 바뀐다 ---------------------------------------------

    @Test
    @DisplayName("주문 뒤 판매 중단된 에셋은 409 PRODUCT_NOT_ON_SALE 이고 주문은 EXPIRED 다")
    void deactivatedAssetExpiresTheOrder() {
        MotifAsset asset = onSaleAsset(AssetType.NORMAL);
        Order order = pendingAssetOrder(asset.getAssetSeq(), "3900.00");
        deactivateAsset(asset.getAssetSeq());

        assertFails(() -> confirm(order, "3900.00"), OrderErrorCode.PRODUCT_NOT_ON_SALE);
        assertThat(reload(order).getStatus()).isEqualTo(OrderStatus.EXPIRED);
        verifyNoInteractions(tossPaymentClient);
    }

    @Test
    @DisplayName("주문 뒤 다른 경로로 이미 산 에셋은 409 ALREADY_OWNED 이고 주문은 EXPIRED 다 — 두 번 팔지 않는다")
    void purchasedMeanwhileExpiresTheOrder() {
        MotifAsset asset = onSaleAsset(AssetType.NORMAL);
        Order order = pendingAssetOrder(asset.getAssetSeq(), "3900.00");
        purchase(asset.getAssetSeq(), fanSeq);

        assertFails(() -> confirm(order, "3900.00"), OrderErrorCode.ALREADY_OWNED);
        assertThat(reload(order).getStatus()).isEqualTo(OrderStatus.EXPIRED);
    }

    @Test
    @DisplayName("주문 뒤 유효 패스가 생긴 NORMAL 에셋은 409 COVERED_BY_PASS 이고 주문은 EXPIRED 다")
    void passGrantedMeanwhileExpiresTheOrder() {
        MotifAsset asset = onSaleAsset(AssetType.NORMAL);
        Order order = pendingAssetOrder(asset.getAssetSeq(), "3900.00");
        activePass(artistSeq, Instant.now().plus(30, ChronoUnit.DAYS));

        assertFails(() -> confirm(order, "3900.00"), OrderErrorCode.COVERED_BY_PASS);
        assertThat(reload(order).getStatus()).isEqualTo(OrderStatus.EXPIRED);
    }

    @Test
    @DisplayName("PASS 주문 뒤 유효 패스가 생겼으면 409 PASS_ALREADY_ACTIVE 이고 주문은 EXPIRED 다")
    void activePassMeanwhileExpiresThePassOrder() {
        passPrice((short) 1, "9900.00");
        Order order = pendingPassOrder((short) 1, "9900.00");
        activePass(artistSeq, Instant.now().plus(30, ChronoUnit.DAYS));

        assertFails(() -> confirm(order, "9900.00"), OrderErrorCode.PASS_ALREADY_ACTIVE);
        assertThat(reload(order).getStatus()).isEqualTo(OrderStatus.EXPIRED);
    }

    @Test
    @DisplayName("주문 뒤 삭제된 아티스트의 PASS 는 404 가 아니라 409 PRODUCT_NOT_ON_SALE 다 — 주문은 있는데 상품이 사라졌다")
    void deletedArtistIsNotOnSaleAtConfirm() {
        passPrice((short) 1, "9900.00");
        Order order = pendingPassOrder((short) 1, "9900.00");
        entityManager.createNativeQuery("update motif_core.artists set is_deleted = true where artist_seq = :seq")
                .setParameter("seq", artistSeq).executeUpdate();
        entityManager.clear();

        assertFails(() -> confirm(order, "9900.00"), OrderErrorCode.PRODUCT_NOT_ON_SALE);
        assertThat(reload(order).getStatus()).isEqualTo(OrderStatus.EXPIRED);
    }

    // --- 픽스처 -------------------------------------------------------------

    private OrderConfirmResponse confirm(Order order, String amount) {
        return confirm(order.getOrderUuid(), order.getOrderUuid().toString(), amount);
    }

    private OrderConfirmResponse confirm(UUID pathUuid, String orderId, String amount) {
        return orderConfirmService.confirm(fanSeq, pathUuid, new OrderConfirmRequest(PAYMENT_KEY, orderId, amount));
    }

    private void tossApproves(Order order, String amount) {
        given(tossPaymentClient.confirm(anyString(), anyString(), any())).willReturn(donePayment(order, amount));
    }

    private static TossPayment donePayment(Order order, String amount) {
        return new TossPayment(PAYMENT_KEY, order.getOrderUuid().toString(), TossPayment.STATUS_DONE,
                new BigDecimal(amount), "카드");
    }

    private Order reload(Order order) {
        entityManager.flush();
        entityManager.clear();
        return orderRepository.findById(order.getOrderSeq()).orElseThrow();
    }

    private void assertFails(ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }

    private Order pendingAssetOrder(Integer assetSeq, String amount) {
        return orderRepository.saveAndFlush(Order.forAsset(fanSeq, assetSeq, "KR", new BigDecimal(amount), "KRW",
                Instant.now().plus(30, ChronoUnit.MINUTES)));
    }

    private Order pendingPassOrder(short months, String amount) {
        return orderRepository.saveAndFlush(Order.forPass(fanSeq, artistSeq, months, "KR", new BigDecimal(amount),
                "KRW", Instant.now().plus(30, ChronoUnit.MINUTES)));
    }

    /** 기간이 지난 PENDING — JPA 는 reg_dttm 을 지금으로 찍으므로 네이티브로 과거에 넣는다. */
    private Long expiredPendingAssetOrder(Integer assetSeq) {
        UUID uuid = UUID.randomUUID();
        entityManager.createNativeQuery("""
                        insert into motif_core.orders
                               (order_uuid, user_seq, product_type, asset_seq, country_code, amount, currency,
                                status, expires_dttm, reg_dttm)
                        values (cast(:uuid as uuid), :userSeq, 'ASSET', :assetSeq, 'KR', 3900.00, 'KRW',
                                'PENDING', now() - interval '1 hour', now() - interval '2 hours')
                        """)
                .setParameter("uuid", uuid.toString())
                .setParameter("userSeq", fanSeq)
                .setParameter("assetSeq", assetSeq)
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

    /** 판매 중 + KR 활성 가격 3900.00 — 12.1 이 주문을 만들 수 있는 에셋. */
    private MotifAsset onSaleAsset(AssetType type) {
        String uuid = UUID.randomUUID().toString();
        MotifAsset asset = motifAssetRepository.saveAndFlush(MotifAsset.register(artistSeq,
                "에셋 " + uuid.substring(0, 8), "소개 문구",
                "assets/" + uuid + ".mp4", "assets/" + uuid + "/thumb.jpg", 9_800, type));
        entityManager.createNativeQuery("""
                        insert into motif_core.motif_asset_prices
                               (asset_seq, country_seq, amount, currency, is_active, reg_usr_seq)
                        values (:assetSeq,
                                (select country_seq from motif_core.countries where country_code = 'KR'),
                                3900.00, 'KRW', true, :regUsrSeq)
                        """)
                .setParameter("assetSeq", asset.getAssetSeq())
                .setParameter("regUsrSeq", adminUserSeq)
                .executeUpdate();
        entityManager.clear();
        return asset;
    }

    private void deactivateAsset(Integer assetSeq) {
        entityManager.createNativeQuery("update motif_core.motif_assets set is_active = false where asset_seq = :seq")
                .setParameter("seq", assetSeq)
                .executeUpdate();
        entityManager.clear();
    }

    private void passPrice(short durationMonths, String amount) {
        entityManager.createNativeQuery("""
                        insert into motif_core.motif_pass_prices
                               (artist_seq, country_seq, duration_months, amount, currency, is_active, reg_usr_seq)
                        values (:artistSeq,
                                (select country_seq from motif_core.countries where country_code = 'KR'),
                                :durationMonths, :amount, 'KRW', true, :regUsrSeq)
                        """)
                .setParameter("artistSeq", artistSeq)
                .setParameter("durationMonths", durationMonths)
                .setParameter("amount", new BigDecimal(amount))
                .setParameter("regUsrSeq", adminUserSeq)
                .executeUpdate();
        entityManager.clear();
    }

    /** 다른 경로(앞선 주문)의 승인이 만든 보유권을 흉내 낸다. */
    private void purchase(Integer assetSeq, Integer userSeq) {
        long orderSeq = paidOrder(userSeq, "ASSET", assetSeq, null);
        entityManager.createNativeQuery("""
                        insert into motif_core.asset_purchases (user_seq, asset_seq, order_seq, status)
                        values (:userSeq, :assetSeq, :orderSeq, 'ACTIVE')
                        """)
                .setParameter("userSeq", userSeq)
                .setParameter("assetSeq", assetSeq)
                .setParameter("orderSeq", orderSeq)
                .executeUpdate();
        entityManager.clear();
    }

    private void activePass(Integer artistSeq, Instant expiresDttm) {
        insertPass(artistSeq, expiresDttm.minus(30, ChronoUnit.DAYS), expiresDttm, "ACTIVE", null);
    }

    /** 기간이 끝난 EXPIRED 패스 — 취소 의사도 남아 있다. 재활성이 둘 다 갈아야 한다. */
    private Integer expiredPass(Instant expiredAt) {
        return insertPass(artistSeq, expiredAt.minus(30, ChronoUnit.DAYS), expiredAt, "EXPIRED",
                expiredAt.minus(5, ChronoUnit.DAYS));
    }

    private Integer insertPass(Integer artistSeq, Instant started, Instant expires, String status, Instant canceled) {
        long orderSeq = paidOrder(fanSeq, "PASS", null, artistSeq);
        entityManager.createNativeQuery("""
                        insert into motif_core.motif_passes
                               (user_seq, artist_seq, order_seq, duration_months, started_dttm, expires_dttm,
                                status, canceled_dttm)
                        values (:userSeq, :artistSeq, :orderSeq, 1, :started, :expires, :status, :canceled)
                        """)
                .setParameter("userSeq", fanSeq)
                .setParameter("artistSeq", artistSeq)
                .setParameter("orderSeq", orderSeq)
                .setParameter("started", started)
                .setParameter("expires", expires)
                .setParameter("status", status)
                .setParameter("canceled", canceled)
                .executeUpdate();
        entityManager.clear();
        return ((Number) entityManager.createNativeQuery(
                        "select pass_seq from motif_core.motif_passes where order_seq = :orderSeq")
                .setParameter("orderSeq", orderSeq)
                .getSingleResult()).intValue();
    }

    private long paidOrder(Integer userSeq, String productType, Integer assetSeq, Integer artistSeq) {
        UUID orderUuid = UUID.randomUUID();
        entityManager.createNativeQuery("""
                        insert into motif_core.orders
                               (order_uuid, user_seq, product_type, asset_seq, artist_seq,
                                country_code, pass_duration_months, amount, currency, status,
                                payment_provider, payment_key, approved_dttm, expires_dttm)
                        values (cast(:orderUuid as uuid), :userSeq, :productType, :assetSeq, :artistSeq,
                                'KR', :passMonths, 3900.00, 'KRW', 'PAID',
                                'TEST', :paymentKey, current_timestamp,
                                current_timestamp + interval '1 day')
                        """)
                .setParameter("orderUuid", orderUuid.toString())
                .setParameter("userSeq", userSeq)
                .setParameter("productType", productType)
                .setParameter("assetSeq", assetSeq)
                .setParameter("artistSeq", artistSeq)
                .setParameter("passMonths", "PASS".equals(productType) ? 1 : null)
                .setParameter("paymentKey", orderUuid.toString())
                .executeUpdate();
        return ((Number) entityManager.createNativeQuery(
                        "select order_seq from motif_core.orders where order_uuid = cast(:orderUuid as uuid)")
                .setParameter("orderUuid", orderUuid.toString())
                .getSingleResult()).longValue();
    }
}
