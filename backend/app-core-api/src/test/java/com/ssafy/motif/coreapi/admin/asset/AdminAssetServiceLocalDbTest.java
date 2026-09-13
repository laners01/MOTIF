package com.ssafy.motif.coreapi.admin.asset;

import com.ssafy.motif.common.error.AdminErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.admin.asset.dto.AssetActiveResponse;
import com.ssafy.motif.coreapi.admin.asset.dto.AssetCreateRequest;
import com.ssafy.motif.coreapi.admin.asset.dto.AssetCreateResponse;
import com.ssafy.motif.coreapi.admin.asset.dto.AssetPriceRequest;
import com.ssafy.motif.coreapi.admin.media.MediaObjectChecker;
import com.ssafy.motif.coreapi.admin.security.AuthenticatedAdmin;
import com.ssafy.motif.coreapi.domain.artist.ArtistErrorCode;
import com.ssafy.motif.domain.core.asset.AssetType;
import com.ssafy.motif.domain.core.asset.MotifAssetPriceRepository;
import com.ssafy.motif.domain.core.asset.MotifAssetRepository;
import com.ssafy.motif.domain.core.country.CountryRepository;
import com.ssafy.motif.domain.core.user.User;
import com.ssafy.motif.domain.core.user.UserRepository;
import com.ssafy.motif.domain.core.user.UserRole;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

/**
 * §3.8.2·§3.8.5·§3.8.6 을 실제 DB에 대고 검증한다 (S15P21D104-568 DoD).
 *
 * <p>핵심은 <b>KR 가격 없는 에셋이 만들어질 수 없다</b>는 것 — 같은 트랜잭션이라
 * 가격 INSERT 가 실패하면 에셋도 남지 않는다.
 *
 * <p>{@link MediaObjectChecker} 만 목이다. HEAD 는 실제 S3 왕복이라 CI·로컬에 버킷과
 * 자격증명이 필요해지는데, 이 테스트가 보려는 것은 업로드 상태가 아니라 트랜잭션
 * 경계다. 나머지(리포지토리·트랜잭션·제약)는 전부 진짜를 쓴다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class AdminAssetServiceLocalDbTest {

    private static final String VIDEO_KEY = "assets/9c1d0000-0000-0000-0000-000000000001.mp4";

    @Autowired
    private AdminAssetService adminAssetService;
    @Autowired
    private MotifAssetRepository assetRepository;
    @Autowired
    private MotifAssetPriceRepository priceRepository;
    @Autowired
    private CountryRepository countryRepository;
    @Autowired
    private UserRepository userRepository;
    @PersistenceContext
    private EntityManager entityManager;

    @MockitoBean
    private MediaObjectChecker mediaObjectChecker;

    private AuthenticatedAdmin admin;
    private Integer artistSeq;

    @BeforeEach
    void setUp() {
        given(mediaObjectChecker.exists(anyString())).willReturn(true);
        admin = new AuthenticatedAdmin(1, newUser().getUserSeq(), "운영자");
        artistSeq = newArtist();
    }

    @Test
    @DisplayName("등록하면 에셋과 KR 활성 가격이 같은 트랜잭션에 생긴다")
    void createMakesAssetAndKrPrice() {
        AssetCreateResponse response = adminAssetService.create(admin, request(new BigDecimal("3900.00")));

        Integer krSeq = countryRepository.findByCountryCode("KR").orElseThrow().getCountrySeq();
        assertThat(priceRepository.findByAssetSeqAndCountrySeq(response.assetSeq(), krSeq))
                .get()
                .satisfies(price -> {
                    assertThat(price.getAmount()).isEqualByComparingTo("3900.00");
                    assertThat(price.getCurrency()).isEqualTo("KRW");
                    assertThat(price.isActive()).isTrue();
                    assertThat(price.getRegUsrSeq()).isEqualTo(admin.userSeq());
                });
        assertThat(response.durationMs()).isEqualTo(9800);
    }

    @Test
    @DisplayName("썸네일 키는 영상 키 규약으로 계산한다 — 파일은 app-media 가 나중에 만든다")
    void thumbnailKeyFollowsConvention() {
        AssetCreateResponse response = adminAssetService.create(admin, request(new BigDecimal("3900.00")));

        assertThat(assetRepository.findById(response.assetSeq()).orElseThrow().getThumbnailKey())
                .isEqualTo("assets/9c1d0000-0000-0000-0000-000000000001/thumb.jpg");
    }

    @Test
    @DisplayName("가격 없는 에셋 행이 하나도 남지 않는다 — 568 DoD 의 사후 조건")
    void noAssetSurvivesWithoutPrice() {
        adminAssetService.create(admin, request(new BigDecimal("3900.00")));
        entityManager.flush();

        // 등록이 한 트랜잭션이므로 가격 INSERT 가 실패하면 에셋도 남지 않는다. 불변식을
        // 경로가 아니라 상태로 본다 — 나중에 create 를 두 트랜잭션으로 쪼개면 여기서 깨진다
        assertThat(entityManager.createNativeQuery("""
                select count(*) from motif_core.motif_assets a
                 where not exists (select 1 from motif_core.motif_asset_prices p
                                    where p.asset_seq = a.asset_seq)
                """).getSingleResult())
                .isEqualTo(0L);
    }

    @Test
    @DisplayName("미존재 아티스트면 404 — 에셋도 가격도 만들지 않는다")
    void unknownArtistCreatesNothing() {
        long before = assetRepository.count();

        assertThatThrownBy(() -> adminAssetService.create(
                admin, new AssetCreateRequest(999_999, "9월 신규 에셋", null, AssetType.NORMAL,
                        VIDEO_KEY, 9800, 1920, 1080,
                        new AssetPriceRequest(new BigDecimal("3900.00"), "KRW", null))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ArtistErrorCode.ARTIST_NOT_FOUND);

        assertThat(assetRepository.count()).isEqualTo(before);
    }

    @Test
    @DisplayName("업로드되지 않은 영상 키는 409 MEDIA_NOT_UPLOADED")
    void missingUploadIsRejected() {
        given(mediaObjectChecker.exists(anyString())).willReturn(false);

        assertThatThrownBy(() -> adminAssetService.create(admin, request(new BigDecimal("3900.00"))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AdminErrorCode.MEDIA_NOT_UPLOADED);
    }

    @Test
    @DisplayName("에셋 비활성화는 가격까지 내리고, 재활성화는 되살리지 않는다 — 경고 카운트로 알린다")
    void deactivateCascadesAndReactivateWarns() {
        Integer assetSeq = adminAssetService.create(admin, request(new BigDecimal("3900.00"))).assetSeq();

        AssetActiveResponse off = adminAssetService.changeActive(admin, assetSeq, false);
        assertThat(off.inactivePriceCount()).isEqualTo(1);

        AssetActiveResponse on = adminAssetService.changeActive(admin, assetSeq, true);
        assertThat(on.isActive()).isTrue();
        assertThat(on.inactivePriceCount()).isEqualTo(1);   // 가격은 여전히 꺼져 있다 (DB §30)
        assertThat(assetRepository.findById(assetSeq).orElseThrow().isActive()).isTrue();
    }

    @Test
    @DisplayName("같은 상태로의 전환은 409 ASSET_STATE_UNCHANGED")
    void sameStateIsRejected() {
        Integer assetSeq = adminAssetService.create(admin, request(new BigDecimal("3900.00"))).assetSeq();

        assertThatThrownBy(() -> adminAssetService.changeActive(admin, assetSeq, true))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AdminErrorCode.ASSET_STATE_UNCHANGED);
    }

    @Test
    @DisplayName("가격 upsert 는 같은 행을 갱신한다 — (에셋, 국가) 당 한 행")
    void upsertUpdatesSameRow() {
        Integer assetSeq = adminAssetService.create(admin, request(new BigDecimal("3900.00"))).assetSeq();

        adminAssetService.upsertPrice(admin, assetSeq, "KR",
                new AssetPriceRequest(new BigDecimal("4900.00"), "KRW", true));
        entityManager.flush();

        Integer krSeq = countryRepository.findByCountryCode("KR").orElseThrow().getCountrySeq();
        assertThat(priceRepository.findByAssetSeqAndCountrySeq(assetSeq, krSeq).orElseThrow().getAmount())
                .isEqualByComparingTo("4900.00");
        assertThat(priceRepository.count()).isEqualTo(countPricesOf(assetSeq));
    }

    @Test
    @DisplayName("비활성 에셋의 가격 활성화는 409 PRICE_ACTIVATION_BLOCKED (DB §31)")
    void priceActivationOnInactiveAssetIsBlocked() {
        Integer assetSeq = adminAssetService.create(admin, request(new BigDecimal("3900.00"))).assetSeq();
        adminAssetService.changeActive(admin, assetSeq, false);

        assertThatThrownBy(() -> adminAssetService.upsertPrice(admin, assetSeq, "KR",
                new AssetPriceRequest(new BigDecimal("4900.00"), "KRW", true)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AdminErrorCode.PRICE_ACTIVATION_BLOCKED);
    }

    @Test
    @DisplayName("미존재 국가는 404 COUNTRY_NOT_FOUND")
    void unknownCountryIsRejected() {
        Integer assetSeq = adminAssetService.create(admin, request(new BigDecimal("3900.00"))).assetSeq();

        assertThatThrownBy(() -> adminAssetService.upsertPrice(admin, assetSeq, "JP",
                new AssetPriceRequest(new BigDecimal("4900.00"), "JPY", true)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AdminErrorCode.COUNTRY_NOT_FOUND);
    }

    // --- 픽스처 -------------------------------------------------------------

    private AssetCreateRequest request(BigDecimal amount) {
        return new AssetCreateRequest(artistSeq, "9월 신규 에셋", "소개 문구", AssetType.NORMAL,
                VIDEO_KEY, 9800, 1920, 1080, new AssetPriceRequest(amount, "KRW", null));
    }

    private long countPricesOf(Integer assetSeq) {
        return ((Number) entityManager.createNativeQuery(
                        "select count(*) from motif_core.motif_asset_prices where asset_seq = :seq")
                .setParameter("seq", assetSeq)
                .getSingleResult()).longValue();
    }

    private User newUser() {
        User user = userRepository.saveAndFlush(User.signUp());
        user.applyInitialNickname();
        return user;
    }

    /** {@code users(ARTIST)} → {@code companies} → {@code artists} FK 체인 (온보딩 테스트와 같은 방식). */
    private Integer newArtist() {
        User artistUser = newUser();
        ReflectionTestUtils.setField(artistUser, "role", UserRole.ARTIST);
        userRepository.flush();

        String companyName = "t-" + UUID.randomUUID().toString().substring(0, 20);
        entityManager.createNativeQuery(
                        "insert into motif_core.companies (company_name, reg_usr_seq) values (:name, :regUsrSeq)")
                .setParameter("name", companyName)
                .setParameter("regUsrSeq", artistUser.getUserSeq())
                .executeUpdate();
        Integer companySeq = ((Number) entityManager.createNativeQuery(
                        "select company_seq from motif_core.companies where company_name = :name")
                .setParameter("name", companyName)
                .getSingleResult()).intValue();

        entityManager.createNativeQuery("""
                        insert into motif_core.artists (user_seq, company_seq, reg_usr_seq)
                        values (:userSeq, :companySeq, :regUsrSeq)
                        """)
                .setParameter("userSeq", artistUser.getUserSeq())
                .setParameter("companySeq", companySeq)
                .setParameter("regUsrSeq", artistUser.getUserSeq())
                .executeUpdate();

        return ((Number) entityManager.createNativeQuery(
                        "select artist_seq from motif_core.artists where user_seq = :userSeq")
                .setParameter("userSeq", artistUser.getUserSeq())
                .getSingleResult()).intValue();
    }
}
