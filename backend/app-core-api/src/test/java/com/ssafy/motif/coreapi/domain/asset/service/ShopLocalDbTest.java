package com.ssafy.motif.coreapi.domain.asset.service;

import com.ssafy.motif.common.response.ListResponse;
import com.ssafy.motif.coreapi.domain.asset.dto.MyAccess;
import com.ssafy.motif.coreapi.domain.asset.dto.ShopSectionItem;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * §11.3 숍 전량을 실제 DB 에 대고 검증한다 (S15P21D104-219).
 *
 * <p><b>그룹핑과 순서가 핵심이다.</b> 아티스트 순서는 구독 조회가, 에셋 순서는 에셋
 * 쿼리가 각각 정하고 서비스가 둘을 합친다 — 합치는 과정에서 순서가 흐트러지는지는
 * DB 없이 확인할 수 없다.
 *
 * <p><b>에셋 0건 아티스트의 섹션이 남는지</b>도 여기서 고정한다. {@code groupingBy} 결과를
 * 그대로 내보내면 조용히 사라지는 자리다.
 *
 * <p><b>로컬 compose DB 는 세 레인이 공유한다</b> — 이 테스트는 자기가 만든 사용자로만
 * 조회하므로 다른 레인의 데이터가 섞이지 않는다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class ShopLocalDbTest {

    @Autowired
    private ShopQueryService shopQueryService;
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
        subscribe(artistSeq);
    }

    // --- 섹션 구성 --------------------------------------------------------------

    @Test
    @DisplayName("구독 아티스트가 섹션이 되고 그 아티스트의 에셋이 담긴다")
    void sectionsCarryTheirOwnAssets() {
        Integer mine = newAsset(artistSeq, AssetType.NORMAL, true).getAssetSeq();

        List<ShopSectionItem> sections = shop();

        assertThat(sections).hasSize(1);
        assertThat(sections.getFirst().artist().artistSeq()).isEqualTo(artistSeq);
        assertThat(sections.getFirst().artist().name()).isEqualTo("카리나");
        assertThat(sections.getFirst().artist().groupName()).isNull();
        assertThat(sections.getFirst().assets())
                .extracting(ShopSectionItem.Asset::assetSeq)
                .containsExactly(mine);
    }

    @Test
    @DisplayName("미구독 아티스트는 섹션도 에셋도 없다")
    void unsubscribedArtistIsAbsent() {
        newAsset(newArtist("윈터"), AssetType.NORMAL, true);
        newAsset(artistSeq, AssetType.NORMAL, true);

        assertThat(shop())
                .extracting(section -> section.artist().artistSeq())
                .containsExactly(artistSeq);
    }

    @Test
    @DisplayName("에셋이 0건인 구독 아티스트도 섹션은 나온다 — §4.7 구독 목록과 어긋나지 않게")
    void artistWithoutAssetsStillGetsASection() {
        List<ShopSectionItem> sections = shop();

        assertThat(sections).hasSize(1);
        assertThat(sections.getFirst().assets()).isEmpty();
    }

    @Test
    @DisplayName("구독이 0건이면 빈 배열이다 — in () 문법 오류로 500 이 나지 않는다")
    void noSubscriptionYieldsEmptyList() {
        Integer stranger = newUser().getUserSeq();
        newAsset(artistSeq, AssetType.NORMAL, true);

        assertThat(shopQueryService.shop(stranger).list()).isEmpty();
    }

    @Test
    @DisplayName("삭제된 아티스트는 섹션에서 빠진다 — 구독 행은 남아 있어도")
    void deletedArtistIsExcluded() {
        newAsset(artistSeq, AssetType.NORMAL, true);
        entityManager.createNativeQuery(
                        "update motif_core.artists set is_deleted = true where artist_seq = :seq")
                .setParameter("seq", artistSeq)
                .executeUpdate();
        entityManager.clear();

        assertThat(shop()).isEmpty();
    }

    // --- 정렬 -------------------------------------------------------------------

    @Test
    @DisplayName("아티스트는 구독 최신순이다 — §4.6·§4.7 과 같은 기준")
    void artistsComeInSubscriptionOrder() {
        Integer later = newArtist("지젤");
        subscribe(later);

        assertThat(shop())
                .extracting(section -> section.artist().artistSeq())
                .containsExactly(later, artistSeq);
    }

    @Test
    @DisplayName("에셋은 섹션 안에서 최신순이다")
    void assetsComeNewestFirstWithinASection() {
        Integer older = newAsset(artistSeq, AssetType.NORMAL, true).getAssetSeq();
        Integer newer = newAsset(artistSeq, AssetType.NORMAL, true).getAssetSeq();

        assertThat(shop().getFirst().assets())
                .extracting(ShopSectionItem.Asset::assetSeq)
                .containsExactly(newer, older);
    }

    @Test
    @DisplayName("에셋이 아티스트별로 갈린다 — 한 섹션에 남의 에셋이 섞이지 않는다")
    void assetsAreGroupedByArtist() {
        Integer second = newArtist("지젤");
        subscribe(second);
        Integer first = newAsset(artistSeq, AssetType.NORMAL, true).getAssetSeq();
        Integer other = newAsset(second, AssetType.NORMAL, true).getAssetSeq();

        List<ShopSectionItem> sections = shop();

        assertThat(sections).hasSize(2);
        assertThat(sections.get(0).assets()).extracting(ShopSectionItem.Asset::assetSeq)
                .containsExactly(other);
        assertThat(sections.get(1).assets()).extracting(ShopSectionItem.Asset::assetSeq)
                .containsExactly(first);
    }

    // --- 노출 규칙 ---------------------------------------------------------------

    @Test
    @DisplayName("판매 중단 에셋은 안 보인다 — 구매도 패스도 없을 때")
    void inactiveAssetIsHiddenWithoutPurchase() {
        newAsset(artistSeq, AssetType.NORMAL, false);

        assertThat(shop().getFirst().assets()).isEmpty();
    }

    @Test
    @DisplayName("판매 중단 에셋도 개별 구매자에게는 보인다")
    void inactiveAssetIsVisibleToBuyer() {
        MotifAsset asset = newAsset(artistSeq, AssetType.NORMAL, false);
        purchase(asset.getAssetSeq());

        assertThat(shop().getFirst().assets())
                .extracting(ShopSectionItem.Asset::assetSeq, ShopSectionItem.Asset::myAccess)
                .containsExactly(
                        org.assertj.core.api.Assertions.tuple(asset.getAssetSeq(), MyAccess.OWNED));
    }

    @Test
    @DisplayName("판매 중단 에셋은 패스만 있는 사용자에게는 안 보인다 — 노출은 구매만 연다")
    void inactiveAssetStaysHiddenWithPassOnly() {
        newAsset(artistSeq, AssetType.NORMAL, false);
        grantPass(artistSeq, Instant.now().plus(30, ChronoUnit.DAYS));

        assertThat(shop().getFirst().assets()).isEmpty();
    }

    // --- 패스 · myAccess ---------------------------------------------------------

    @Test
    @DisplayName("패스를 보유하면 섹션 헤더가 그것을 말하고 에셋은 PASS 로 열린다")
    void passShowsInHeaderAndOpensAssets() {
        newAsset(artistSeq, AssetType.NORMAL, true);
        Instant expires = Instant.now().plus(30, ChronoUnit.DAYS);
        grantPass(artistSeq, expires);

        ShopSectionItem section = shop().getFirst();

        assertThat(section.passActive()).isTrue();
        assertThat(section.expiresDttm()).isCloseTo(expires,
                org.assertj.core.api.Assertions.within(1, ChronoUnit.MILLIS));
        assertThat(section.assets().getFirst().myAccess()).isEqualTo(MyAccess.PASS);
    }

    @Test
    @DisplayName("만료된 패스는 보유가 아니다 — 에셋도 NONE 이다")
    void expiredPassIsNotHeld() {
        newAsset(artistSeq, AssetType.NORMAL, true);
        grantPass(artistSeq, Instant.now().minus(1, ChronoUnit.DAYS));

        ShopSectionItem section = shop().getFirst();

        assertThat(section.passActive()).isFalse();
        assertThat(section.expiresDttm()).isNull();
        assertThat(section.assets().getFirst().myAccess()).isEqualTo(MyAccess.NONE);
    }

    @Test
    @DisplayName("패스는 아티스트별로 갈린다 — 한쪽 패스가 다른 섹션을 열지 않는다")
    void passAppliesToItsOwnArtistOnly() {
        Integer second = newArtist("지젤");
        subscribe(second);
        newAsset(artistSeq, AssetType.NORMAL, true);
        newAsset(second, AssetType.NORMAL, true);
        grantPass(artistSeq, Instant.now().plus(30, ChronoUnit.DAYS));

        List<ShopSectionItem> sections = shop();

        ShopSectionItem withPass = sections.stream()
                .filter(s -> s.artist().artistSeq().equals(artistSeq)).findFirst().orElseThrow();
        ShopSectionItem without = sections.stream()
                .filter(s -> s.artist().artistSeq().equals(second)).findFirst().orElseThrow();

        assertThat(withPass.passActive()).isTrue();
        assertThat(withPass.assets().getFirst().myAccess()).isEqualTo(MyAccess.PASS);
        assertThat(without.passActive()).isFalse();
        assertThat(without.assets().getFirst().myAccess()).isEqualTo(MyAccess.NONE);
    }

    @Test
    @DisplayName("PREMIUM 은 유효 패스가 있어도 NONE 이다")
    void premiumIsNotCoveredByPass() {
        newAsset(artistSeq, AssetType.PREMIUM, true);
        grantPass(artistSeq, Instant.now().plus(30, ChronoUnit.DAYS));

        assertThat(shop().getFirst().assets().getFirst().myAccess()).isEqualTo(MyAccess.NONE);
    }

    @Test
    @DisplayName("SECRET 은 패스로 열린다 — NORMAL 과 같은 규칙")
    void secretIsCoveredByPass() {
        newAsset(artistSeq, AssetType.SECRET, true);
        grantPass(artistSeq, Instant.now().plus(30, ChronoUnit.DAYS));

        assertThat(shop().getFirst().assets().getFirst().myAccess()).isEqualTo(MyAccess.PASS);
    }

    @Test
    @DisplayName("구매와 패스가 동시에 유효하면 OWNED 다")
    void purchaseWinsOverPass() {
        MotifAsset asset = newAsset(artistSeq, AssetType.NORMAL, true);
        purchase(asset.getAssetSeq());
        grantPass(artistSeq, Instant.now().plus(30, ChronoUnit.DAYS));

        assertThat(shop().getFirst().assets().getFirst().myAccess()).isEqualTo(MyAccess.OWNED);
    }

    @Test
    @DisplayName("남의 구매·남의 패스는 내 응답을 바꾸지 않는다")
    void otherUsersDoNotAffectMe() {
        MotifAsset asset = newAsset(artistSeq, AssetType.NORMAL, true);
        Integer otherFan = newUser().getUserSeq();
        purchase(asset.getAssetSeq(), otherFan);
        grantPass(otherFan, artistSeq, Instant.now().plus(30, ChronoUnit.DAYS));

        ShopSectionItem section = shop().getFirst();

        assertThat(section.passActive()).isFalse();
        assertThat(section.assets().getFirst().myAccess()).isEqualTo(MyAccess.NONE);
    }

    // --- 픽스처 -------------------------------------------------------------

    private List<ShopSectionItem> shop() {
        ListResponse<ShopSectionItem> response = shopQueryService.shop(fanSeq);
        return response.list();
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

    private void grantPass(Integer artistSeq, Instant expiresDttm) {
        grantPass(fanSeq, artistSeq, expiresDttm);
    }

    private void grantPass(Integer userSeq, Integer artistSeq, Instant expiresDttm) {
        long orderSeq = newOrder(userSeq, "PASS", null, artistSeq);
        entityManager.createNativeQuery("""
                        insert into motif_core.motif_passes
                               (user_seq, artist_seq, order_seq, duration_months,
                                started_dttm, expires_dttm, status)
                        values (:userSeq, :artistSeq, :orderSeq, 1,
                                :startedDttm, :expiresDttm, 'ACTIVE')
                        """)
                .setParameter("userSeq", userSeq)
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
