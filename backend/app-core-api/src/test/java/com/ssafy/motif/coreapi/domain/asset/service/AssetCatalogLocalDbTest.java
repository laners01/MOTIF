package com.ssafy.motif.coreapi.domain.asset.service;

import com.ssafy.motif.common.response.PageResponse;
import com.ssafy.motif.coreapi.domain.asset.dto.AssetCatalogItem;
import com.ssafy.motif.coreapi.domain.asset.dto.MyAccess;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * §11.1 에셋 목록을 실제 DB 에 대고 검증한다 (S15P21D104-224).
 *
 * <p><b>여기서 볼 것은 쿼리다.</b> 구독 필터 · 노출 필터 · 권한 판정이 한 네이티브
 * 쿼리에 들어 있어 세 조건이 서로를 가리는지는 DB 없이 확인할 수 없다. 우선순위 규칙
 * 자체는 {@code MyAccessTest} 가 DB 없이 고정한다.
 *
 * <p><b>{@code cast(:artistSeq as integer)} 의 {@code null} 바인딩을 반드시 덮는다</b> —
 * 기본 모드가 곧 {@code artistSeq=null} 이라, 캐스트가 빠졌다면 <b>가장 흔한 호출</b>이
 * 500 이 되는 자리다({@code ArtistRepository.search} 의 {@code lower(bytea)} 사고와 같은 원인).
 *
 * <p><b>로컬 compose DB 는 세 레인이 공유한다</b> — 단언은 이 테스트가 만든 아티스트의
 * 에셋으로만 좁힌다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
@TestPropertySource(properties = "motif.media.public-base-url=" + AssetCatalogLocalDbTest.CDN)
@Transactional
class AssetCatalogLocalDbTest {

    /**
     * 운영 CDN base URL ({@code application-prod.yml} 의 {@code motif.media.public-base-url}
     * fallback 과 같은 값이다).
     *
     * <p><b>왜 주입하나.</b> 로컬·CI 프로필에는 이 값이 없어
     * {@code PublicMediaUrlResolver} 가 모든 URL 을 조용히 {@code null} 로 돌려준다 —
     * 그대로 두면 "키가 아니라 URL 로 나가는가"를 확인할 수 없고, 변환이 통째로 빠져도
     * 테스트가 통과한다.
     */
    static final String CDN = "https://d21so2cpi8si9r.cloudfront.net";

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
    private Integer subscribedArtistSeq;
    private Integer strangerArtistSeq;

    @BeforeEach
    void setUp() {
        adminUserSeq = newUser().getUserSeq();
        fanSeq = newUser().getUserSeq();
        subscribedArtistSeq = newArtist("카리나");
        strangerArtistSeq = newArtist("윈터");
        subscribe(subscribedArtistSeq);
    }

    // --- 기본 모드 (artistSeq 생략) ------------------------------------------

    @Test
    @DisplayName("쿼리 없이 부르면 구독 아티스트 에셋만 온다 — 미구독 아티스트는 빠진다")
    void defaultModeReturnsSubscribedOnly() {
        Integer mine = newAsset(subscribedArtistSeq, AssetType.NORMAL, true).getAssetSeq();
        newAsset(strangerArtistSeq, AssetType.NORMAL, true);

        assertThat(assets(null).list())
                .extracting(AssetCatalogItem::assetSeq)
                .containsExactly(mine);
    }

    @Test
    @DisplayName("artistSeq 를 생략한 최초 호출이 500 이 아니다 — null 바인딩 캐스트 방어")
    void defaultModeDoesNotBreakOnNullBinding() {
        newAsset(subscribedArtistSeq, AssetType.NORMAL, true);

        PageResponse<AssetCatalogItem> page = assets(null);

        assertThat(page.list()).hasSize(1);
        assertThat(page.totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("구독이 0건이면 빈 목록이다 — 실패가 아니다")
    void noSubscriptionYieldsEmptyPage() {
        newAsset(strangerArtistSeq, AssetType.NORMAL, true);
        Integer stranger = newUser().getUserSeq();

        PageResponse<AssetCatalogItem> page =
                assetQueryService.assets(stranger, null, 0, 20);

        assertThat(page.list()).isEmpty();
        assertThat(page.totalElements()).isZero();
    }

    @Test
    @DisplayName("삭제된 아티스트의 구독은 목록에서 빠진다 — 눌러도 404 인 카드를 만들지 않는다")
    void deletedArtistIsExcludedFromSubscribedMode() {
        newAsset(subscribedArtistSeq, AssetType.NORMAL, true);
        deleteArtist(subscribedArtistSeq);

        assertThat(assets(null).list()).isEmpty();
    }

    @Test
    @DisplayName("구독 아티스트가 여럿이면 섞여서 최신순이다")
    void multipleArtistsAreMixedByRecency() {
        Integer another = newArtist("지젤");
        subscribe(another);

        Integer older = newAsset(subscribedArtistSeq, AssetType.NORMAL, true).getAssetSeq();
        Integer newer = newAsset(another, AssetType.NORMAL, true).getAssetSeq();

        assertThat(assets(null).list())
                .extracting(AssetCatalogItem::assetSeq)
                .containsExactly(newer, older);
    }

    // --- artistSeq 모드 -------------------------------------------------------

    @Test
    @DisplayName("artistSeq 를 주면 미구독 아티스트도 조회된다 — 아티스트 상세에서 들어오는 경로다")
    void artistModeIgnoresSubscription() {
        Integer stranger = newAsset(strangerArtistSeq, AssetType.NORMAL, true).getAssetSeq();

        assertThat(assets(strangerArtistSeq).list())
                .extracting(AssetCatalogItem::assetSeq)
                .containsExactly(stranger);
    }

    @Test
    @DisplayName("artistSeq 를 주면 그 아티스트 것만 온다 — 구독한 다른 아티스트가 섞이지 않는다")
    void artistModeNarrowsToOneArtist() {
        newAsset(subscribedArtistSeq, AssetType.NORMAL, true);
        Integer stranger = newAsset(strangerArtistSeq, AssetType.NORMAL, true).getAssetSeq();

        assertThat(assets(strangerArtistSeq).list())
                .extracting(AssetCatalogItem::assetSeq)
                .containsExactly(stranger);
    }

    @Test
    @DisplayName("없는 artistSeq 는 빈 목록 200 이다 — 존재를 검증하지 않는다")
    void unknownArtistYieldsEmptyPage() {
        newAsset(subscribedArtistSeq, AssetType.NORMAL, true);

        assertThat(assets(Integer.MAX_VALUE).list()).isEmpty();
    }

    // --- 노출 규칙 -------------------------------------------------------------

    @Test
    @DisplayName("판매 중단 에셋은 안 보인다 — 구매도 패스도 없을 때")
    void inactiveAssetIsHiddenWithoutPurchase() {
        newAsset(subscribedArtistSeq, AssetType.NORMAL, false);

        assertThat(assets(null).list()).isEmpty();
    }

    @Test
    @DisplayName("판매 중단 에셋도 개별 구매자에게는 보인다 — 산 것은 판매가 끊겨도 산 것이다")
    void inactiveAssetIsVisibleToBuyer() {
        MotifAsset asset = newAsset(subscribedArtistSeq, AssetType.NORMAL, false);
        purchase(asset.getAssetSeq());

        assertThat(assets(null).list())
                .extracting(AssetCatalogItem::assetSeq, AssetCatalogItem::myAccess)
                .containsExactly(tuple(asset.getAssetSeq(), MyAccess.OWNED));
    }

    @Test
    @DisplayName("판매 중단 에셋은 패스만 있는 사용자에게는 안 보인다 — 노출은 구매만 연다")
    void inactiveAssetStaysHiddenWithPassOnly() {
        newAsset(subscribedArtistSeq, AssetType.NORMAL, false);
        grantPass(subscribedArtistSeq, Instant.now().plus(30, ChronoUnit.DAYS));

        assertThat(assets(null).list()).isEmpty();
    }

    @Test
    @DisplayName("비활성 에셋이 빠진 만큼 totalElements 도 줄어든다 — 페이지 경계가 항목 수와 맞는다")
    void hiddenAssetsAreNotCounted() {
        Integer visible = newAsset(subscribedArtistSeq, AssetType.NORMAL, true).getAssetSeq();
        newAsset(subscribedArtistSeq, AssetType.NORMAL, false);

        PageResponse<AssetCatalogItem> page = assets(null);

        assertThat(page.list()).extracting(AssetCatalogItem::assetSeq).containsExactly(visible);
        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.hasNext()).isFalse();
    }

    // --- myAccess --------------------------------------------------------------

    @Test
    @DisplayName("유효 패스가 있으면 PASS 다")
    void activePassGivesPass() {
        newAsset(subscribedArtistSeq, AssetType.NORMAL, true);
        grantPass(subscribedArtistSeq, Instant.now().plus(30, ChronoUnit.DAYS));

        assertThat(assets(null).list().getFirst().myAccess()).isEqualTo(MyAccess.PASS);
    }

    @Test
    @DisplayName("만료된 패스는 권한이 아니다 — status 만 보면 틀리는 구간")
    void expiredPassIsNotAccess() {
        newAsset(subscribedArtistSeq, AssetType.NORMAL, true);
        grantPass(subscribedArtistSeq, Instant.now().minus(1, ChronoUnit.DAYS));

        assertThat(assets(null).list().getFirst().myAccess()).isEqualTo(MyAccess.NONE);
    }

    @Test
    @DisplayName("다른 아티스트의 패스는 이 에셋을 열지 않는다")
    void passOfAnotherArtistDoesNotCover() {
        newAsset(subscribedArtistSeq, AssetType.NORMAL, true);
        grantPass(strangerArtistSeq, Instant.now().plus(30, ChronoUnit.DAYS));

        assertThat(assets(null).list().getFirst().myAccess()).isEqualTo(MyAccess.NONE);
    }

    @Test
    @DisplayName("PREMIUM 은 유효 패스가 있어도 NONE 이다")
    void premiumIsNotCoveredByPass() {
        newAsset(subscribedArtistSeq, AssetType.PREMIUM, true);
        grantPass(subscribedArtistSeq, Instant.now().plus(30, ChronoUnit.DAYS));

        assertThat(assets(null).list().getFirst().myAccess()).isEqualTo(MyAccess.NONE);
    }

    @Test
    @DisplayName("SECRET 은 패스로 열린다 — NORMAL 과 같은 규칙")
    void secretIsCoveredByPass() {
        newAsset(subscribedArtistSeq, AssetType.SECRET, true);
        grantPass(subscribedArtistSeq, Instant.now().plus(30, ChronoUnit.DAYS));

        assertThat(assets(null).list().getFirst().myAccess()).isEqualTo(MyAccess.PASS);
    }

    @Test
    @DisplayName("남이 산 에셋은 내 응답에서 NONE 이다 — 판정이 호출자 기준이다")
    void accessIsPerCaller() {
        MotifAsset asset = newAsset(subscribedArtistSeq, AssetType.NORMAL, true);
        Integer otherFan = newUser().getUserSeq();
        purchase(asset.getAssetSeq(), otherFan);

        assertThat(assets(null).list().getFirst().myAccess()).isEqualTo(MyAccess.NONE);
    }

    // --- 항목 --------------------------------------------------------------------

    @Test
    @DisplayName("항목의 artist 는 README §3.5 요약이다 — 솔로면 groupName 이 null")
    void itemCarriesArtistSummary() {
        newAsset(subscribedArtistSeq, AssetType.NORMAL, true);

        AssetCatalogItem.Artist artist = assets(null).list().getFirst().artist();

        assertThat(artist.artistSeq()).isEqualTo(subscribedArtistSeq);
        assertThat(artist.name()).isEqualTo("카리나");
        assertThat(artist.groupName()).isNull();
    }

    @Test
    @DisplayName("썸네일은 키가 아니라 URL 로 나간다 — 에셋은 공개 등급이라 무서명이다")
    void thumbnailIsResolvedToUrl() {
        MotifAsset asset = newAsset(subscribedArtistSeq, AssetType.NORMAL, true);

        AssetCatalogItem item = assets(null).list().getFirst();

        assertThat(item.thumbnailUrl()).isEqualTo(CDN + "/" + asset.getThumbnailKey());
    }

    @Test
    @DisplayName("프로필 이미지가 없으면 null 이다 — 항목은 그대로 온다")
    void missingProfileImageIsNull() {
        newAsset(subscribedArtistSeq, AssetType.NORMAL, true);

        assertThat(assets(null).list().getFirst().artist().profileImageUrl()).isNull();
    }

    @Test
    @DisplayName("size 는 100 을 넘지 못한다 — §1.5 상한")
    void sizeIsCappedAtHundred() {
        newAsset(subscribedArtistSeq, AssetType.NORMAL, true);

        assertThat(assetQueryService.assets(fanSeq, null, 0, 5_000).size())
                .isEqualTo(100);
    }

    // --- 픽스처 -------------------------------------------------------------

    private PageResponse<AssetCatalogItem> assets(Integer artistSeq) {
        return assetQueryService.assets(fanSeq, artistSeq, 0, 20);
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

    private void subscribe(Integer artistSeq) {
        subscriptionRepository.insertIfAbsent(fanSeq, artistSeq);
        entityManager.clear();
    }

    private void deleteArtist(Integer artistSeq) {
        entityManager.createNativeQuery(
                        "update motif_core.artists set is_deleted = true where artist_seq = :seq")
                .setParameter("seq", artistSeq)
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

    /** 패스도 마찬가지다 — 결제 흐름(§12)이 만들 행을 여기서는 직접 넣는다. */
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
