package com.ssafy.motif.coreapi.domain.asset.service;

import com.ssafy.motif.coreapi.domain.asset.dto.AssetCatalogItem;
import com.ssafy.motif.domain.core.artist.Artist;
import com.ssafy.motif.domain.core.artist.ArtistRepository;
import com.ssafy.motif.domain.core.artist.SubscriptionRepository;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * §11.1 내 보유함(구매한 에셋)을 실제 DB 에 대고 검증한다 (S15P21D104-219).
 *
 * <p><b>"보지 않는 것" 이 계약이다.</b> 판매 중단 · 구독 해제 · 아티스트 삭제 — 셋 다
 * 구매를 무효로 만들지 않는다. 조회가 {@code asset_purchases} 에서 출발하는지,
 * 아니면 에셋 쪽 조건이 남아 있는지는 DB 없이 확인할 수 없다.
 *
 * <p>정렬도 여기서만 검증된다 — <b>구매 최신순</b>이지 에셋 최신순이 아니다. 오래된
 * 에셋을 오늘 사면 맨 위로 온다.
 *
 * <p><b>로컬 compose DB 는 세 레인이 공유한다</b> — 이 테스트는 자기가 만든 사용자로만
 * 조회하므로 다른 레인의 데이터가 섞이지 않는다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
@TestPropertySource(properties = "motif.media.public-base-url=" + AssetCatalogLocalDbTest.CDN)
@Transactional
class AssetCatalogLocalDbTest {

    /**
     * 로컬·CI 프로필에는 CDN base URL 이 없어 {@code PublicMediaUrlResolver} 가 모든
     * URL 을 {@code null} 로 돌려준다 — 그대로 두면 썸네일이 키인지 URL 인지 구분되지 않는다.
     */
    static final String CDN = "https://cdn.test";

    @Autowired
    private AssetQueryService assetQueryService;
    @Autowired
    private ArtistRepository artistRepository;
    @Autowired
    private MotifAssetRepository motifAssetRepository;
    @Autowired
    private SubscriptionRepository subscriptionRepository;
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
        artistSeq = newArtist("카리나");
    }

    // --- 대상 ------------------------------------------------------------------

    @Test
    @DisplayName("내가 산 에셋만 나온다 — 사지 않은 것은 빠진다")
    void onlyPurchasedAssetsAppear() {
        MotifAsset bought = newAsset(artistSeq, AssetType.NORMAL, true);
        newAsset(artistSeq, AssetType.NORMAL, true);
        purchase(bought.getAssetSeq());

        assertThat(assets(null))
                .extracting(AssetCatalogItem::assetSeq)
                .containsExactly(bought.getAssetSeq());
    }

    @Test
    @DisplayName("구매가 0건이면 빈 목록이다")
    void noPurchaseYieldsEmptyList() {
        newAsset(artistSeq, AssetType.NORMAL, true);

        assertThat(assets(null)).isEmpty();
    }

    @Test
    @DisplayName("남의 구매는 내 보유함에 없다")
    void purchaseOfAnotherUserIsNotMine() {
        MotifAsset asset = newAsset(artistSeq, AssetType.NORMAL, true);
        purchase(asset.getAssetSeq(), newUser().getUserSeq());

        assertThat(assets(null)).isEmpty();
    }

    @Test
    @DisplayName("패스로만 열리는 에셋은 나오지 않는다 — 산 것이 아니다")
    void passDoesNotPutAssetInLibrary() {
        newAsset(artistSeq, AssetType.NORMAL, true);
        grantPass(artistSeq, Instant.now().plus(30, ChronoUnit.DAYS));

        assertThat(assets(null)).isEmpty();
    }

    @Test
    @DisplayName("환불된 구매는 빠진다 — ACTIVE 만 보유다")
    void refundedPurchaseIsExcluded() {
        MotifAsset asset = newAsset(artistSeq, AssetType.NORMAL, true);
        purchase(asset.getAssetSeq(), fanSeq, "REFUNDED");

        assertThat(assets(null)).isEmpty();
    }

    // --- 보지 않는 것 -----------------------------------------------------------

    @Test
    @DisplayName("판매가 중단돼도 내 보유는 남는다")
    void inactiveAssetStaysInLibrary() {
        MotifAsset asset = newAsset(artistSeq, AssetType.NORMAL, false);
        purchase(asset.getAssetSeq());

        assertThat(assets(null))
                .extracting(AssetCatalogItem::assetSeq)
                .containsExactly(asset.getAssetSeq());
    }

    @Test
    @DisplayName("구독을 하지 않아도·끊어도 산 것은 남는다")
    void subscriptionIsIrrelevant() {
        MotifAsset asset = newAsset(artistSeq, AssetType.NORMAL, true);
        purchase(asset.getAssetSeq());
        subscriptionRepository.insertIfAbsent(fanSeq, artistSeq);
        entityManager.clear();

        assertThat(assets(null)).hasSize(1);

        subscriptionRepository.deleteIfPresent(fanSeq, artistSeq);
        entityManager.clear();

        assertThat(assets(null)).hasSize(1);
    }

    @Test
    @DisplayName("아티스트가 삭제돼도 남고, 이름·프로필은 계속 나온다")
    void deletedArtistStillAppears() {
        MotifAsset asset = newAsset(artistSeq, AssetType.NORMAL, true);
        purchase(asset.getAssetSeq());
        entityManager.createNativeQuery(
                        "update motif_core.artists set is_deleted = true where artist_seq = :seq")
                .setParameter("seq", artistSeq)
                .executeUpdate();
        entityManager.clear();

        List<AssetCatalogItem> items = assets(null);

        assertThat(items).hasSize(1);
        assertThat(items.getFirst().artist().name()).isEqualTo("카리나");
    }

    // --- 정렬 · 필터 ------------------------------------------------------------

    @Test
    @DisplayName("구매 최신순이다 — 오래된 에셋을 나중에 사면 위로 온다")
    void orderedByPurchaseRecency() {
        MotifAsset older = newAsset(artistSeq, AssetType.NORMAL, true);
        MotifAsset newer = newAsset(artistSeq, AssetType.NORMAL, true);
        purchase(newer.getAssetSeq());          // 먼저 산 것
        purchase(older.getAssetSeq());          // 나중에 산 것 — 에셋은 더 오래됐다

        assertThat(assets(null))
                .extracting(AssetCatalogItem::assetSeq)
                .containsExactly(older.getAssetSeq(), newer.getAssetSeq());
    }

    @Test
    @DisplayName("artistSeq 를 주면 그 아티스트에게서 산 것만 남는다")
    void artistFilterNarrowsTheLibrary() {
        Integer other = newArtist("윈터");
        MotifAsset mine = newAsset(artistSeq, AssetType.NORMAL, true);
        MotifAsset theirs = newAsset(other, AssetType.NORMAL, true);
        purchase(mine.getAssetSeq());
        purchase(theirs.getAssetSeq());

        assertThat(assets(artistSeq))
                .extracting(AssetCatalogItem::assetSeq)
                .containsExactly(mine.getAssetSeq());
    }

    @Test
    @DisplayName("없는 artistSeq 는 빈 목록 200 이다 — 존재를 검증하지 않는다")
    void unknownArtistYieldsEmptyList() {
        MotifAsset asset = newAsset(artistSeq, AssetType.NORMAL, true);
        purchase(asset.getAssetSeq());

        assertThat(assets(Integer.MAX_VALUE)).isEmpty();
    }

    @Test
    @DisplayName("artistSeq 를 생략한 호출이 500 이 아니다 — null 바인딩 캐스트 방어")
    void nullArtistSeqBindingIsSafe() {
        MotifAsset asset = newAsset(artistSeq, AssetType.NORMAL, true);
        purchase(asset.getAssetSeq());

        assertThat(assetQueryService.assets(fanSeq, null).list()).hasSize(1);
    }

    // --- 항목 --------------------------------------------------------------------

    @Test
    @DisplayName("항목의 artist 는 README §3.5 요약이다 — 솔로면 groupName 이 null")
    void itemCarriesArtistSummary() {
        MotifAsset asset = newAsset(artistSeq, AssetType.NORMAL, true);
        purchase(asset.getAssetSeq());

        AssetCatalogItem.Artist artist = assets(null).getFirst().artist();

        assertThat(artist.artistSeq()).isEqualTo(artistSeq);
        assertThat(artist.name()).isEqualTo("카리나");
        assertThat(artist.groupName()).isNull();
        assertThat(artist.profileImageUrl()).isNull();
    }

    @Test
    @DisplayName("썸네일은 키가 아니라 URL 로 나간다")
    void thumbnailIsResolvedToUrl() {
        MotifAsset asset = newAsset(artistSeq, AssetType.NORMAL, true);
        purchase(asset.getAssetSeq());

        assertThat(assets(null).getFirst().thumbnailUrl())
                .isEqualTo(CDN + "/" + asset.getThumbnailKey());
    }

    // --- 픽스처 -------------------------------------------------------------

    private List<AssetCatalogItem> assets(Integer artistSeq) {
        return assetQueryService.assets(fanSeq, artistSeq).list();
    }

    private User newUser() {
        User user = userRepository.saveAndFlush(User.signUp());
        user.applyInitialNickname();
        return user;
    }

    private Integer newArtist(String name) {
        User artistUser = userRepository.saveAndFlush(User.createArtist(name));
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

    private MotifAsset newAsset(Integer artistSeq, AssetType type, boolean active) {
        String uuid = UUID.randomUUID().toString();
        MotifAsset asset = MotifAsset.register(artistSeq, "에셋 " + uuid.substring(0, 8), "소개 문구",
                "assets/" + uuid + ".mp4", "assets/" + uuid + "/thumb.jpg", 9_800, type);
        if (!active) {
            asset.changeActive(false);
        }
        return motifAssetRepository.saveAndFlush(asset);
    }

    /** 구매 도메인(§12)이 아직 없어 행을 직접 넣는다 — 검증 대상은 조회 쪽이다. */
    private void purchase(Integer assetSeq) {
        purchase(assetSeq, fanSeq, "ACTIVE");
    }

    private void purchase(Integer assetSeq, Integer userSeq) {
        purchase(assetSeq, userSeq, "ACTIVE");
    }

    private void purchase(Integer assetSeq, Integer userSeq, String status) {
        long orderSeq = newOrder(userSeq, "ASSET", assetSeq, null);
        entityManager.createNativeQuery("""
                        insert into motif_core.asset_purchases (user_seq, asset_seq, order_seq, status)
                        values (:userSeq, :assetSeq, :orderSeq, :status)
                        """)
                .setParameter("userSeq", userSeq)
                .setParameter("assetSeq", assetSeq)
                .setParameter("orderSeq", orderSeq)
                .setParameter("status", status)
                .executeUpdate();
        entityManager.clear();
    }

    private void grantPass(Integer artistSeq, Instant expiresDttm) {
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
