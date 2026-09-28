package com.ssafy.motif.coreapi.domain.asset.service;

import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.domain.asset.AssetErrorCode;
import com.ssafy.motif.coreapi.domain.asset.dto.AssetDetailResponse;
import com.ssafy.motif.coreapi.domain.asset.dto.MyAccess;
import com.ssafy.motif.domain.core.artist.Artist;
import com.ssafy.motif.domain.core.artist.ArtistRepository;
import com.ssafy.motif.domain.core.asset.AssetType;
import com.ssafy.motif.domain.core.asset.MotifAsset;
import com.ssafy.motif.domain.core.asset.MotifAssetRepository;
import com.ssafy.motif.domain.core.user.User;
import com.ssafy.motif.domain.core.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * §11.2 에셋 상세를 실제 DB 에 대고 검증한다 (S15P21D104-221).
 *
 * <p><b>404 세 갈래를 반드시 덮는다</b> — 미존재 · 판매 중단 + 권한 없음 · 판매 중단 +
 * 패스만 보유. 셋이 한 코드로 모여야 존재 은닉(§1.8)이 성립하는데, 노출 조건이 쿼리
 * 안에 있어 DB 없이는 확인할 수 없다.
 *
 * <p>가격 조인도 여기서만 검증된다 — KR 활성 행이 없을 때 상세가 <b>열리되</b>
 * {@code price:null}·{@code purchasable:false} 여야 한다. {@code left join} 을
 * {@code join} 으로 쓰면 가격 없는 에셋이 통째로 404 가 되는데, 그 사고가 여기서 잡힌다.
 *
 * <p><b>로컬 compose DB 는 세 레인이 공유한다</b> — 단언은 이 테스트가 만든 에셋으로만
 * 좁힌다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
@TestPropertySource(properties = "motif.media.public-base-url=" + AssetDetailLocalDbTest.CDN)
@Transactional
class AssetDetailLocalDbTest {

    /**
     * 로컬·CI 프로필에는 CDN base URL 이 없어 {@code PublicMediaUrlResolver} 가 모든
     * URL 을 조용히 {@code null} 로 돌려준다 — 그대로 두면 {@code previewVideoUrl} 이
     * 키를 그대로 내보내도 테스트가 통과한다.
     */
    static final String CDN = "https://cdn.test";

    @Autowired
    private AssetQueryService assetQueryService;
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
    @DisplayName("판매 중 에셋은 구독하지 않아도 열린다 — 아티스트 상세에서 들어오는 경로다")
    void detailIgnoresSubscription() {
        MotifAsset asset = newAsset(AssetType.NORMAL, true);
        price(asset.getAssetSeq(), "3900.00");

        AssetDetailResponse detail = detail(asset.getAssetSeq());

        assertThat(detail.assetSeq()).isEqualTo(asset.getAssetSeq());
        assertThat(detail.title()).isEqualTo(asset.getTitle());
        assertThat(detail.durationMs()).isEqualTo(9_800);
        assertThat(detail.assetType()).isEqualTo(AssetType.NORMAL);
    }

    @Test
    @DisplayName("artist 는 README §3.5 요약이다 — 솔로면 groupName 이 null")
    void detailCarriesArtistSummary() {
        MotifAsset asset = newAsset(AssetType.NORMAL, true);

        AssetDetailResponse.Artist artist = detail(asset.getAssetSeq()).artist();

        assertThat(artist.artistSeq()).isEqualTo(artistSeq);
        assertThat(artist.name()).isEqualTo("카리나");
        assertThat(artist.groupName()).isNull();
        assertThat(artist.profileImageUrl()).isNull();
    }

    // --- previewVideoUrl --------------------------------------------------------

    @Test
    @DisplayName("가공 전에는 업로드 MP4 키가 그대로 URL 이 된다 — 서버가 master.m3u8 을 붙이지 않는다")
    void previewUrlBeforeProcessingIsTheUploadedMp4() {
        MotifAsset asset = newAsset(AssetType.NORMAL, true);

        AssetDetailResponse detail = detail(asset.getAssetSeq());

        assertThat(detail.previewVideoUrl()).isEqualTo(CDN + "/" + asset.getVideoKey());
        assertThat(detail.previewVideoUrl()).endsWith(".mp4");
    }

    @Test
    @DisplayName("가공이 끝나면 video_key 가 마스터 재생목록이라 그대로 나간다 — 보호 경로가 아니다")
    void previewUrlAfterProcessingIsTheMasterPlaylist() {
        MotifAsset asset = newAsset(AssetType.NORMAL, true);
        String master = "assets/" + UUID.randomUUID() + "/master.m3u8";
        replaceKeys(asset.getAssetSeq(), master);

        AssetDetailResponse detail = detail(asset.getAssetSeq());

        assertThat(detail.previewVideoUrl()).isEqualTo(CDN + "/" + master);
        assertThat(detail.previewVideoUrl()).doesNotContain("/api/v1/media/");
    }

    // --- price · purchasable ----------------------------------------------------

    @Test
    @DisplayName("가격은 문자열이다 — NUMERIC 을 부동소수로 보내지 않는다")
    void priceAmountIsAPlainString() {
        MotifAsset asset = newAsset(AssetType.NORMAL, true);
        price(asset.getAssetSeq(), "3900.00");

        AssetDetailResponse.Price price = detail(asset.getAssetSeq()).price();

        assertThat(price.amount()).isEqualTo("3900.00");
        assertThat(price.currency()).isEqualTo("KRW");
    }

    @Test
    @DisplayName("KR 가격이 없으면 상세는 열리고 price 만 null 이다 — left join 이라 404 가 아니다")
    void missingPriceStillOpensTheDetail() {
        MotifAsset asset = newAsset(AssetType.NORMAL, true);

        AssetDetailResponse detail = detail(asset.getAssetSeq());

        assertThat(detail.price()).isNull();
        assertThat(detail.purchasable()).isFalse();
    }

    @Test
    @DisplayName("꺼진 가격은 없는 것과 같다 — 팬에게는 비활성 가격이 보이지 않는다")
    void inactivePriceIsTreatedAsMissing() {
        MotifAsset asset = newAsset(AssetType.NORMAL, true);
        price(asset.getAssetSeq(), "3900.00");
        deactivatePrices(asset.getAssetSeq());

        AssetDetailResponse detail = detail(asset.getAssetSeq());

        assertThat(detail.price()).isNull();
        assertThat(detail.purchasable()).isFalse();
    }

    @Test
    @DisplayName("판매 중 + 가격 있음 + 미보유면 구매 가능하다")
    void purchasableWhenOnSale() {
        MotifAsset asset = newAsset(AssetType.NORMAL, true);
        price(asset.getAssetSeq(), "3900.00");

        assertThat(detail(asset.getAssetSeq()).purchasable()).isTrue();
    }

    @Test
    @DisplayName("이미 산 에셋은 구매 불가다 — DB 가 ACTIVE 구매를 하나로 막는다")
    void ownedIsNotPurchasable() {
        MotifAsset asset = newAsset(AssetType.NORMAL, true);
        price(asset.getAssetSeq(), "3900.00");
        purchase(asset.getAssetSeq());

        AssetDetailResponse detail = detail(asset.getAssetSeq());

        assertThat(detail.myAccess()).isEqualTo(MyAccess.OWNED);
        assertThat(detail.purchasable()).isFalse();
    }

    @Test
    @DisplayName("패스로 열린 에셋은 구매 가능하다 — 패스는 만료되므로 영구 보유를 따로 산다")
    void passAccessIsStillPurchasable() {
        MotifAsset asset = newAsset(AssetType.NORMAL, true);
        price(asset.getAssetSeq(), "3900.00");
        grantPass(Instant.now().plus(30, ChronoUnit.DAYS));

        AssetDetailResponse detail = detail(asset.getAssetSeq());

        assertThat(detail.myAccess()).isEqualTo(MyAccess.PASS);
        assertThat(detail.purchasable()).isTrue();
    }

    @Test
    @DisplayName("PREMIUM 은 유효 패스가 있어도 NONE 이고, 그래서 구매 가능하다")
    void premiumStaysPurchasableWithPass() {
        MotifAsset asset = newAsset(AssetType.PREMIUM, true);
        price(asset.getAssetSeq(), "9900.00");
        grantPass(Instant.now().plus(30, ChronoUnit.DAYS));

        AssetDetailResponse detail = detail(asset.getAssetSeq());

        assertThat(detail.myAccess()).isEqualTo(MyAccess.NONE);
        assertThat(detail.purchasable()).isTrue();
    }

    // --- 404 --------------------------------------------------------------------

    @Test
    @DisplayName("없는 에셋은 404 다")
    void unknownAssetIsNotFound() {
        assertNotFound(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("판매 중단 에셋은 권한 없는 사용자에게 404 다 — 존재를 숨긴다")
    void inactiveAssetIsNotFoundWithoutPurchase() {
        assertNotFound(newAsset(AssetType.NORMAL, false).getAssetSeq());
    }

    @Test
    @DisplayName("판매 중단 에셋은 패스만 보유한 사용자에게도 404 다 — §11.1 노출 규칙과 같다")
    void inactiveAssetIsNotFoundWithPassOnly() {
        MotifAsset asset = newAsset(AssetType.NORMAL, false);
        grantPass(Instant.now().plus(30, ChronoUnit.DAYS));

        assertNotFound(asset.getAssetSeq());
    }

    @Test
    @DisplayName("판매 중단 에셋도 개별 구매자에게는 200 이다 — 산 것은 판매가 끊겨도 산 것이다")
    void inactiveAssetOpensForBuyer() {
        MotifAsset asset = newAsset(AssetType.NORMAL, false);
        price(asset.getAssetSeq(), "3900.00");
        purchase(asset.getAssetSeq());

        AssetDetailResponse detail = detail(asset.getAssetSeq());

        assertThat(detail.myAccess()).isEqualTo(MyAccess.OWNED);
        assertThat(detail.purchasable()).isFalse();
    }

    @Test
    @DisplayName("남이 산 에셋은 내게 404 다 — 판정이 호출자 기준이다")
    void purchaseOfAnotherUserDoesNotOpenIt() {
        MotifAsset asset = newAsset(AssetType.NORMAL, false);
        purchase(asset.getAssetSeq(), newUser().getUserSeq());

        assertNotFound(asset.getAssetSeq());
    }

    // --- 픽스처 -------------------------------------------------------------

    private AssetDetailResponse detail(Integer assetSeq) {
        return assetQueryService.detail(fanSeq, assetSeq);
    }

    private void assertNotFound(Integer assetSeq) {
        assertThatThrownBy(() -> detail(assetSeq))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AssetErrorCode.ASSET_NOT_FOUND);
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

    /** 가공 완료를 흉내 낸다 — app-media 가 video_key 를 마스터 재생목록으로 덮어쓴다. */
    private void replaceKeys(Integer assetSeq, String masterKey) {
        entityManager.createNativeQuery(
                        "update motif_core.motif_assets set video_key = :key where asset_seq = :seq")
                .setParameter("key", masterKey)
                .setParameter("seq", assetSeq)
                .executeUpdate();
        entityManager.clear();
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

    private void deactivatePrices(Integer assetSeq) {
        entityManager.createNativeQuery(
                        "update motif_core.motif_asset_prices set is_active = false where asset_seq = :seq")
                .setParameter("seq", assetSeq)
                .executeUpdate();
        entityManager.clear();
    }

    /** 구매 도메인(§12)이 아직 없어 행을 직접 넣는다 — 검증 대상은 조회 쪽이다. */
    private void purchase(Integer assetSeq) {
        purchase(assetSeq, fanSeq);
    }

    private void purchase(Integer assetSeq, Integer userSeq) {
        long orderSeq = newOrder(userSeq, "ASSET", assetSeq, null);
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

    private void grantPass(Instant expiresDttm) {
        long orderSeq = newOrder(fanSeq, "PASS", null, artistSeq);
        entityManager.createNativeQuery("""
                        insert into motif_core.motif_passes
                               (user_seq, artist_seq, order_seq, duration_months,
                                started_dttm, expires_dttm, status)
                        values (:userSeq, :artistSeq, :orderSeq, 1,
                                :startedDttm, :expiresDttm, 'ACTIVE')
                        """)
                .setParameter("userSeq", fanSeq)
                .setParameter("artistSeq", artistSeq)
                .setParameter("orderSeq", orderSeq)
                .setParameter("startedDttm", expiresDttm.minus(30, ChronoUnit.DAYS))
                .setParameter("expiresDttm", expiresDttm)
                .executeUpdate();
        entityManager.clear();
    }

    private long newOrder(Integer userSeq, String productType, Integer assetSeq, Integer artistSeq) {
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
