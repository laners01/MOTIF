package com.ssafy.motif.coreapi.domain.order.service;

import com.ssafy.motif.common.error.CommonErrorCode;
import com.ssafy.motif.common.error.ErrorCode;
import com.ssafy.motif.common.error.FieldErrorResponse;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.domain.artist.ArtistErrorCode;
import com.ssafy.motif.coreapi.domain.asset.AssetErrorCode;
import com.ssafy.motif.coreapi.domain.order.OrderErrorCode;
import com.ssafy.motif.coreapi.domain.order.dto.OrderCreateRequest;
import com.ssafy.motif.coreapi.domain.order.dto.OrderCreateResponse;
import com.ssafy.motif.domain.core.artist.Artist;
import com.ssafy.motif.domain.core.artist.ArtistRepository;
import com.ssafy.motif.domain.core.asset.AssetType;
import com.ssafy.motif.domain.core.asset.MotifAsset;
import com.ssafy.motif.domain.core.asset.MotifAssetRepository;
import com.ssafy.motif.domain.core.order.Order;
import com.ssafy.motif.domain.core.order.OrderRepository;
import com.ssafy.motif.domain.core.order.OrderStatus;
import com.ssafy.motif.domain.core.order.ProductType;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.within;

/**
 * §12.1 주문 생성을 실제 DB 에 대고 검증한다 (S15P21D104-263).
 *
 * <p><b>스냅샷이 핵심이다.</b> 응답의 금액·통화가 가격 행에서 왔고 {@code orders} 에 같은
 * 값이 {@code PENDING} 으로 남아야 한다. {@code ck_orders_product_shape}·
 * {@code ck_orders_common}·{@code ck_orders_status_shape} 는 INSERT 시점에 DB 가 검사하므로
 * 이 테스트가 통과했다는 것은 팩토리가 만든 행이 CHECK 를 전부 지킨다는 뜻이다.
 *
 * <p>판정 쪽은 <b>§11.2 와 다른 두 지점</b>을 본다 — 판매 중단 에셋이 404 가 아니라 409 이고,
 * 유효 패스 판정이 {@code status} 가 아니라 DB {@code now()} 기준이라 기간이 지난 ACTIVE
 * 행은 주문을 막지 않는다.
 *
 * <p><b>로컬 compose DB 는 세 레인이 공유한다</b> — 단언은 이 테스트가 만든 사용자·아티스트·
 * 에셋으로만 좁힌다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class OrderCreateLocalDbTest {

    @Autowired
    private OrderCommandService orderCommandService;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private ArtistRepository artistRepository;
    @Autowired
    private MotifAssetRepository motifAssetRepository;
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

    // --- 성공 ------------------------------------------------------------------

    @Test
    @DisplayName("ASSET — KR 활성 가격을 스냅샷한 PENDING 주문이 생기고 응답은 위젯 파라미터다")
    void assetOrderSnapshotsPrice() {
        MotifAsset asset = newAsset(AssetType.NORMAL, true);
        price(asset.getAssetSeq(), "3900.00");

        OrderCreateResponse response = createAsset(asset.getAssetSeq());

        assertThat(response.orderUuid()).isNotNull();
        assertThat(response.orderName()).isEqualTo("카리나 모티프 에셋");
        assertThat(response.amount()).isEqualTo("3900.00");
        assertThat(response.currency()).isEqualTo("KRW");

        Order order = reload(response.orderUuid());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getProductType()).isEqualTo(ProductType.ASSET);
        assertThat(order.getUserSeq()).isEqualTo(fanSeq);
        assertThat(order.getAssetSeq()).isEqualTo(asset.getAssetSeq());
        assertThat(order.getArtistSeq()).isNull();
        assertThat(order.getPassDurationMonths()).isNull();
        assertThat(order.getCountryCode()).isEqualTo("KR");
        assertThat(order.getAmount()).isEqualByComparingTo("3900.00");
        assertThat(order.getCurrency()).isEqualTo("KRW");
        assertThat(order.getPaymentProvider()).isNull();
        assertThat(order.getPaymentKey()).isNull();
        assertThat(order.getApprovedDttm()).isNull();
        assertThat(order.getFailReason()).isNull();
    }

    @Test
    @DisplayName("만료는 생성 시각 + 30분이고 응답의 expiresDttm 과 같다")
    void expiresThirtyMinutesAfterCreation() {
        MotifAsset asset = newAsset(AssetType.NORMAL, true);
        price(asset.getAssetSeq(), "3900.00");

        OrderCreateResponse response = createAsset(asset.getAssetSeq());

        Order order = reload(response.orderUuid());
        assertThat(order.getExpiresDttm())
                .isCloseTo(order.getRegDttm().plus(30, ChronoUnit.MINUTES), within(5, ChronoUnit.SECONDS));
        // TIMESTAMPTZ 왕복에서 나노초가 잘린다 — 밀리초까지만 같으면 된다
        assertThat(response.expiresDttm()).isCloseTo(order.getExpiresDttm(), within(1, ChronoUnit.MILLIS));
    }

    @Test
    @DisplayName("PASS — 요청한 개월의 KR 활성 가격을 스냅샷하고 artist_seq·pass_duration_months 를 채운다")
    void passOrderSnapshotsPrice() {
        passPrice((short) 1, "9900.00");
        passPrice((short) 3, "27900.00");

        OrderCreateResponse response = createPass((short) 3);

        assertThat(response.orderName()).isEqualTo("카리나 모티프 패스 3개월");
        assertThat(response.amount()).isEqualTo("27900.00");
        assertThat(response.currency()).isEqualTo("KRW");

        Order order = reload(response.orderUuid());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getProductType()).isEqualTo(ProductType.PASS);
        assertThat(order.getArtistSeq()).isEqualTo(artistSeq);
        assertThat(order.getPassDurationMonths()).isEqualTo((short) 3);
        assertThat(order.getAssetSeq()).isNull();
        assertThat(order.getCountryCode()).isEqualTo("KR");
        assertThat(order.getAmount()).isEqualByComparingTo("27900.00");
    }

    @Test
    @DisplayName("같은 상품의 PENDING 주문이 있어도 새 주문이 생긴다 — 시도마다 새 행이다")
    void duplicatePendingOrdersAreAllowed() {
        MotifAsset asset = newAsset(AssetType.NORMAL, true);
        price(asset.getAssetSeq(), "3900.00");

        OrderCreateResponse first = createAsset(asset.getAssetSeq());
        OrderCreateResponse second = createAsset(asset.getAssetSeq());

        assertThat(second.orderUuid()).isNotEqualTo(first.orderUuid());
        assertThat(reload(first.orderUuid()).getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(reload(second.orderUuid()).getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("PREMIUM 은 유효 패스가 있어도 살 수 있다 — 패스로 열리지 않는 상품이다")
    void premiumIsPurchasableDespitePass() {
        MotifAsset asset = newAsset(AssetType.PREMIUM, true);
        price(asset.getAssetSeq(), "9900.00");
        grantPass(artistSeq, Instant.now().plus(30, ChronoUnit.DAYS), "ACTIVE");

        assertThat(createAsset(asset.getAssetSeq()).amount()).isEqualTo("9900.00");
    }

    // --- 404 --------------------------------------------------------------------

    @Test
    @DisplayName("없는 에셋은 404 ASSET_NOT_FOUND 다")
    void unknownAssetIsNotFound() {
        assertFails(() -> createAsset(Integer.MAX_VALUE), AssetErrorCode.ASSET_NOT_FOUND);
    }

    @Test
    @DisplayName("없는 아티스트는 404 ARTIST_NOT_FOUND 다")
    void unknownArtistIsNotFound() {
        assertFails(() -> orderCommandService.create(fanSeq,
                        new OrderCreateRequest(ProductType.PASS, null, Integer.MAX_VALUE, (short) 1)),
                ArtistErrorCode.ARTIST_NOT_FOUND);
    }

    @Test
    @DisplayName("삭제된 아티스트는 가격이 남아 있어도 404 다")
    void deletedArtistIsNotFound() {
        passPrice((short) 1, "9900.00");
        entityManager.createNativeQuery(
                        "update motif_core.artists set is_deleted = true where artist_seq = :seq")
                .setParameter("seq", artistSeq)
                .executeUpdate();
        entityManager.clear();

        assertFails(() -> createPass((short) 1), ArtistErrorCode.ARTIST_NOT_FOUND);
    }

    // --- PRODUCT_NOT_ON_SALE ----------------------------------------------------

    @Test
    @DisplayName("판매 중단 에셋은 409 다 — §11.2 와 달리 존재를 숨기지 않는다")
    void inactiveAssetIsNotOnSale() {
        MotifAsset asset = newAsset(AssetType.NORMAL, false);
        price(asset.getAssetSeq(), "3900.00");

        assertFails(() -> createAsset(asset.getAssetSeq()), OrderErrorCode.PRODUCT_NOT_ON_SALE);
    }

    @Test
    @DisplayName("KR 가격이 없으면 판매 중이어도 409 다 — 금액을 정할 수 없다")
    void assetWithoutPriceIsNotOnSale() {
        MotifAsset asset = newAsset(AssetType.NORMAL, true);

        assertFails(() -> createAsset(asset.getAssetSeq()), OrderErrorCode.PRODUCT_NOT_ON_SALE);
    }

    @Test
    @DisplayName("꺼진 가격은 없는 것과 같다 — 409")
    void assetWithInactivePriceIsNotOnSale() {
        MotifAsset asset = newAsset(AssetType.NORMAL, true);
        price(asset.getAssetSeq(), "3900.00");
        deactivateAssetPrices(asset.getAssetSeq());

        assertFails(() -> createAsset(asset.getAssetSeq()), OrderErrorCode.PRODUCT_NOT_ON_SALE);
    }

    @Test
    @DisplayName("PASS 는 요청한 개월의 가격이 있어야 한다 — 다른 개월만 있으면 409")
    void passWithoutPriceForRequestedMonthsIsNotOnSale() {
        passPrice((short) 3, "27900.00");

        assertFails(() -> createPass((short) 1), OrderErrorCode.PRODUCT_NOT_ON_SALE);
    }

    @Test
    @DisplayName("꺼진 PASS 가격은 없는 것과 같다 — 409")
    void passWithInactivePriceIsNotOnSale() {
        passPrice((short) 1, "9900.00");
        deactivatePassPrice((short) 1);

        assertFails(() -> createPass((short) 1), OrderErrorCode.PRODUCT_NOT_ON_SALE);
    }

    // --- ALREADY_OWNED ----------------------------------------------------------

    @Test
    @DisplayName("이미 산 에셋은 409 ALREADY_OWNED 다")
    void ownedAssetIsRejected() {
        MotifAsset asset = newAsset(AssetType.NORMAL, true);
        price(asset.getAssetSeq(), "3900.00");
        purchase(asset.getAssetSeq(), fanSeq);

        assertFails(() -> createAsset(asset.getAssetSeq()), OrderErrorCode.ALREADY_OWNED);
    }

    @Test
    @DisplayName("남이 산 에셋은 내 주문을 막지 않는다 — 판정이 호출자 기준이다")
    void purchaseOfAnotherUserDoesNotBlock() {
        MotifAsset asset = newAsset(AssetType.NORMAL, true);
        price(asset.getAssetSeq(), "3900.00");
        purchase(asset.getAssetSeq(), newUser().getUserSeq());

        assertThat(createAsset(asset.getAssetSeq()).orderUuid()).isNotNull();
    }

    @Test
    @DisplayName("개별 구매와 패스가 둘 다 있으면 ALREADY_OWNED 다 — OWNED > PASS")
    void ownedWinsOverPass() {
        MotifAsset asset = newAsset(AssetType.NORMAL, true);
        price(asset.getAssetSeq(), "3900.00");
        purchase(asset.getAssetSeq(), fanSeq);
        grantPass(artistSeq, Instant.now().plus(30, ChronoUnit.DAYS), "ACTIVE");

        assertFails(() -> createAsset(asset.getAssetSeq()), OrderErrorCode.ALREADY_OWNED);
    }

    // --- COVERED_BY_PASS --------------------------------------------------------

    @Test
    @DisplayName("유효 패스로 열린 NORMAL 에셋은 409 COVERED_BY_PASS 다")
    void normalAssetCoveredByPass() {
        MotifAsset asset = newAsset(AssetType.NORMAL, true);
        price(asset.getAssetSeq(), "3900.00");
        grantPass(artistSeq, Instant.now().plus(30, ChronoUnit.DAYS), "ACTIVE");

        assertFails(() -> createAsset(asset.getAssetSeq()), OrderErrorCode.COVERED_BY_PASS);
    }

    @Test
    @DisplayName("SECRET 은 NORMAL 과 같다 — 패스가 덮으면 409")
    void secretAssetCoveredByPass() {
        MotifAsset asset = newAsset(AssetType.SECRET, true);
        price(asset.getAssetSeq(), "3900.00");
        grantPass(artistSeq, Instant.now().plus(30, ChronoUnit.DAYS), "ACTIVE");

        assertFails(() -> createAsset(asset.getAssetSeq()), OrderErrorCode.COVERED_BY_PASS);
    }

    @Test
    @DisplayName("기간이 지난 ACTIVE 패스는 덮지 않는다 — 만료 배치가 아직 없어 DB 시각으로 판정한다")
    void expiredPassDoesNotCover() {
        MotifAsset asset = newAsset(AssetType.NORMAL, true);
        price(asset.getAssetSeq(), "3900.00");
        grantPass(artistSeq, Instant.now().minus(1, ChronoUnit.DAYS), "ACTIVE");

        assertThat(createAsset(asset.getAssetSeq()).orderUuid()).isNotNull();
    }

    @Test
    @DisplayName("다른 아티스트의 패스는 이 에셋을 덮지 않는다")
    void passOfAnotherArtistDoesNotCover() {
        MotifAsset asset = newAsset(AssetType.NORMAL, true);
        price(asset.getAssetSeq(), "3900.00");
        grantPass(newArtist(), Instant.now().plus(30, ChronoUnit.DAYS), "ACTIVE");

        assertThat(createAsset(asset.getAssetSeq()).orderUuid()).isNotNull();
    }

    // --- PASS_ALREADY_ACTIVE ----------------------------------------------------

    @Test
    @DisplayName("유효 패스가 있으면 PASS 주문은 409 PASS_ALREADY_ACTIVE 다")
    void activePassBlocksRepurchase() {
        passPrice((short) 1, "9900.00");
        grantPass(artistSeq, Instant.now().plus(30, ChronoUnit.DAYS), "ACTIVE");

        assertFails(() -> createPass((short) 1), OrderErrorCode.PASS_ALREADY_ACTIVE);
    }

    @Test
    @DisplayName("기간이 지난 ACTIVE 패스는 재구매를 막지 않는다")
    void expiredPassAllowsRepurchase() {
        passPrice((short) 1, "9900.00");
        grantPass(artistSeq, Instant.now().minus(1, ChronoUnit.DAYS), "ACTIVE");

        assertThat(createPass((short) 1).orderName()).isEqualTo("카리나 모티프 패스 1개월");
    }

    @Test
    @DisplayName("환불된 패스는 기간이 남아 있어도 재구매를 막지 않는다")
    void refundedPassAllowsRepurchase() {
        passPrice((short) 1, "9900.00");
        grantPass(artistSeq, Instant.now().plus(30, ChronoUnit.DAYS), "REFUNDED");

        assertThat(createPass((short) 1).orderUuid()).isNotNull();
    }

    @Test
    @DisplayName("남의 패스는 내 PASS 주문을 막지 않는다")
    void passOfAnotherUserDoesNotBlock() {
        passPrice((short) 1, "9900.00");
        grantPass(newUser().getUserSeq(), artistSeq, Instant.now().plus(30, ChronoUnit.DAYS), "ACTIVE");

        assertThat(createPass((short) 1).orderUuid()).isNotNull();
    }

    // --- 400 --------------------------------------------------------------------

    @Test
    @DisplayName("형태 불일치는 400 INVALID_PARAMETER 이고 data 에 필드 오류가 실린다")
    void shapeViolationIsInvalidParameterWithFieldErrors() {
        BusinessException e = catchThrowableOfType(BusinessException.class,
                () -> orderCommandService.create(fanSeq,
                        new OrderCreateRequest(ProductType.ASSET, 21, 3, null)));

        assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_PARAMETER);
        assertThat(e.getData()).isInstanceOf(List.class);
        assertThat((List<?>) e.getData())
                .singleElement()
                .isEqualTo(new FieldErrorResponse("artistSeq", "ASSET 주문에는 보내지 않습니다."));
    }

    // --- 픽스처 -------------------------------------------------------------

    private OrderCreateResponse createAsset(Integer assetSeq) {
        return orderCommandService.create(fanSeq,
                new OrderCreateRequest(ProductType.ASSET, assetSeq, null, null));
    }

    private OrderCreateResponse createPass(short months) {
        return orderCommandService.create(fanSeq,
                new OrderCreateRequest(ProductType.PASS, null, artistSeq, months));
    }

    /** 영속성 컨텍스트를 비우고 DB 에 실제로 남은 행을 읽는다. */
    private Order reload(UUID orderUuid) {
        entityManager.flush();
        entityManager.clear();
        return orderRepository.findByOrderUuid(orderUuid).orElseThrow();
    }

    private void assertFails(ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
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

    private MotifAsset newAsset(AssetType type, boolean active) {
        String uuid = UUID.randomUUID().toString();
        MotifAsset asset = MotifAsset.register(artistSeq, "에셋 " + uuid.substring(0, 8), "소개 문구",
                "assets/" + uuid + ".mp4", "assets/" + uuid + "/thumb.jpg", 9_800, type);
        if (!active) {
            asset.changeActive(false);
        }
        return motifAssetRepository.saveAndFlush(asset);
    }

    private void price(Integer assetSeq, String amount) {
        entityManager.createNativeQuery("""
                        insert into motif_core.motif_asset_prices
                               (asset_seq, country_seq, amount, currency, is_active, reg_usr_seq)
                        values (:assetSeq,
                                (select country_seq from motif_core.countries where country_code = 'KR'),
                                :amount, 'KRW', true, :regUsrSeq)
                        """)
                .setParameter("assetSeq", assetSeq)
                .setParameter("amount", new BigDecimal(amount))
                .setParameter("regUsrSeq", adminUserSeq)
                .executeUpdate();
        entityManager.clear();
    }

    private void deactivateAssetPrices(Integer assetSeq) {
        entityManager.createNativeQuery(
                        "update motif_core.motif_asset_prices set is_active = false where asset_seq = :seq")
                .setParameter("seq", assetSeq)
                .executeUpdate();
        entityManager.clear();
    }

    private void passPrice(short durationMonths, String amount) {
        entityManager.createNativeQuery("""
                        insert into motif_core.motif_pass_prices
                               (artist_seq, country_seq, duration_months, amount, currency,
                                is_active, reg_usr_seq)
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

    private void deactivatePassPrice(short durationMonths) {
        entityManager.createNativeQuery("""
                        update motif_core.motif_pass_prices set is_active = false
                         where artist_seq = :artistSeq and duration_months = :durationMonths
                        """)
                .setParameter("artistSeq", artistSeq)
                .setParameter("durationMonths", durationMonths)
                .executeUpdate();
        entityManager.clear();
    }

    /** 승인(§12.2)이 만들 권한 행을 직접 넣는다 — 검증 대상은 주문 생성 쪽이다. */
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

    private void grantPass(Integer artistSeq, Instant expiresDttm, String status) {
        grantPass(fanSeq, artistSeq, expiresDttm, status);
    }

    private void grantPass(Integer userSeq, Integer artistSeq, Instant expiresDttm, String status) {
        long orderSeq = paidOrder(userSeq, "PASS", null, artistSeq);
        entityManager.createNativeQuery("""
                        insert into motif_core.motif_passes
                               (user_seq, artist_seq, order_seq, duration_months,
                                started_dttm, expires_dttm, status)
                        values (:userSeq, :artistSeq, :orderSeq, 1,
                                :startedDttm, :expiresDttm, :status)
                        """)
                .setParameter("userSeq", userSeq)
                .setParameter("artistSeq", artistSeq)
                .setParameter("orderSeq", orderSeq)
                .setParameter("startedDttm", expiresDttm.minus(30, ChronoUnit.DAYS))
                .setParameter("expiresDttm", expiresDttm)
                .setParameter("status", status)
                .executeUpdate();
        entityManager.clear();
    }

    /** 권한 행이 참조할 PAID 주문 — 이 티켓의 서비스는 PENDING 만 만들므로 직접 넣는다. */
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
