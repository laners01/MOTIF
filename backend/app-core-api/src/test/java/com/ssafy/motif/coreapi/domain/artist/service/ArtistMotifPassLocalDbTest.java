package com.ssafy.motif.coreapi.domain.artist.service;

import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.domain.artist.ArtistErrorCode;
import com.ssafy.motif.coreapi.domain.artist.dto.ArtistMotifPassResponse;
import com.ssafy.motif.domain.core.artist.Artist;
import com.ssafy.motif.domain.core.artist.ArtistRepository;
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
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;

/**
 * §11.4 아티스트별 모티프 패스를 실제 DB 에 대고 검증한다.
 *
 * <p><b>만료 판정이 핵심이다.</b> {@code status='ACTIVE'} 인데 기간이 지난 행이 실제로
 * 존재한다 — 만료 배치가 아직 없다. 그 구간을 자바 시계로 비교했다면 앱 서버와 DB 의
 * 차이만큼 판정이 갈렸을 자리라, DB 의 {@code now()} 로 거른다는 계약을 여기서 고정한다.
 *
 * <p>가격 쪽은 <b>꺼진 행이 빠지는지</b>와 <b>개월 오름차순</b>을 본다. 어드민 가격표
 * (§3.9.1)는 꺼진 행까지 보여 주므로, 같은 테이블을 읽는 두 조회가 서로 다른 집합을
 * 낸다는 것이 계약이다.
 *
 * <p><b>로컬 compose DB 는 세 레인이 공유한다</b> — 단언은 이 테스트가 만든 아티스트로만
 * 좁힌다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
@TestPropertySource(properties = "motif.media.public-base-url=" + ArtistMotifPassLocalDbTest.CDN)
@Transactional
class ArtistMotifPassLocalDbTest {

    /**
     * 로컬·CI 프로필에는 CDN base URL 이 없어 {@code PublicMediaUrlResolver} 가 모든
     * URL 을 {@code null} 로 돌려준다 — 그대로 두면 배너가 키인지 URL 인지 구분되지 않는다.
     */
    static final String CDN = "https://cdn.test";

    @Autowired
    private ArtistMotifPassQueryService artistMotifPassQueryService;
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
        artistSeq = newArtist("artists/" + UUID.randomUUID() + "/motif-bg.jpg");
    }

    // --- 미보유 ----------------------------------------------------------------

    @Test
    @DisplayName("미보유면 passActive=false, expiresDttm=null, prices 는 개월 오름차순")
    void withoutPassPricesComeInMonthOrder() {
        price((short) 3, "27900.00");
        price((short) 1, "9900.00");

        ArtistMotifPassResponse response = motifPass();

        assertThat(response.artistSeq()).isEqualTo(artistSeq);
        assertThat(response.passActive()).isFalse();
        assertThat(response.expiresDttm()).isNull();
        assertThat(response.prices())
                .extracting(ArtistMotifPassResponse.Price::durationMonths,
                        ArtistMotifPassResponse.Price::amount,
                        ArtistMotifPassResponse.Price::currency)
                .containsExactly(
                        tuple((short) 1, "9900.00", "KRW"),
                        tuple((short) 3, "27900.00", "KRW"));
    }

    @Test
    @DisplayName("가격이 0건이면 빈 배열 200 이다 — 판매 준비 중인 아티스트")
    void noPriceYieldsEmptyArray() {
        ArtistMotifPassResponse response = motifPass();

        assertThat(response.passActive()).isFalse();
        assertThat(response.prices()).isEmpty();
    }

    @Test
    @DisplayName("꺼진 가격은 나오지 않는다 — 어드민 가격표(§3.9.1)와 집합이 다르다")
    void inactivePriceIsExcluded() {
        price((short) 1, "9900.00");
        price((short) 3, "27900.00");
        deactivatePrice((short) 3);

        assertThat(motifPass().prices())
                .extracting(ArtistMotifPassResponse.Price::durationMonths)
                .containsExactly((short) 1);
    }

    @Test
    @DisplayName("다른 아티스트의 가격은 섞이지 않는다")
    void pricesOfAnotherArtistDoNotLeak() {
        price((short) 1, "9900.00");
        Integer other = newArtist(null);
        price(other, (short) 6, "49900.00");

        assertThat(motifPass().prices())
                .extracting(ArtistMotifPassResponse.Price::durationMonths)
                .containsExactly((short) 1);
    }

    // --- 보유 중 ---------------------------------------------------------------

    @Test
    @DisplayName("보유 중이면 passActive=true, expiresDttm 이 있고 prices 는 빈 배열이다")
    void heldPassHidesPrices() {
        price((short) 1, "9900.00");
        Instant expires = Instant.now().plus(30, ChronoUnit.DAYS);
        grantPass(artistSeq, expires, "ACTIVE");

        ArtistMotifPassResponse response = motifPass();

        assertThat(response.passActive()).isTrue();
        // TIMESTAMPTZ 왕복에서 나노초가 잘린다 — 밀리초까지만 같으면 된다
        assertThat(response.expiresDttm()).isCloseTo(expires, within(1, ChronoUnit.MILLIS));
        assertThat(response.prices()).isEmpty();
    }

    @Test
    @DisplayName("기간이 지난 ACTIVE 패스는 미보유다 — 만료 배치가 아직 없다")
    void expiredPassIsNotHeld() {
        price((short) 1, "9900.00");
        grantPass(artistSeq, Instant.now().minus(1, ChronoUnit.DAYS), "ACTIVE");

        ArtistMotifPassResponse response = motifPass();

        assertThat(response.passActive()).isFalse();
        assertThat(response.expiresDttm()).isNull();
        assertThat(response.prices()).hasSize(1);
    }

    @Test
    @DisplayName("환불된 패스는 기간이 남아 있어도 미보유다")
    void refundedPassIsNotHeld() {
        grantPass(artistSeq, Instant.now().plus(30, ChronoUnit.DAYS), "REFUNDED");

        assertThat(motifPass().passActive()).isFalse();
    }

    @Test
    @DisplayName("다른 아티스트의 패스는 이 응답을 바꾸지 않는다")
    void passOfAnotherArtistDoesNotCount() {
        Integer other = newArtist(null);
        grantPass(other, Instant.now().plus(30, ChronoUnit.DAYS), "ACTIVE");

        assertThat(motifPass().passActive()).isFalse();
    }

    @Test
    @DisplayName("남의 패스는 내 응답에서 보이지 않는다 — 판정이 호출자 기준이다")
    void passOfAnotherUserDoesNotCount() {
        grantPass(newUser().getUserSeq(), artistSeq,
                Instant.now().plus(30, ChronoUnit.DAYS), "ACTIVE");

        assertThat(motifPass().passActive()).isFalse();
    }

    // --- 배너 · 404 -------------------------------------------------------------

    @Test
    @DisplayName("배너는 키가 아니라 URL 로 나간다")
    void bannerIsResolvedToUrl() {
        Artist artist = artistRepository.findById(artistSeq).orElseThrow();

        assertThat(motifPass().bannerImageUrl())
                .isEqualTo(CDN + "/" + artist.getMotifBgImageKey());
    }

    @Test
    @DisplayName("배너 키가 없으면 null 이다 — 응답 자체는 정상이다")
    void missingBannerIsNull() {
        Integer bare = newArtist(null);

        assertThat(artistMotifPassQueryService.motifPass(fanSeq, bare).bannerImageUrl()).isNull();
    }

    @Test
    @DisplayName("없는 아티스트는 404 다")
    void unknownArtistIsNotFound() {
        assertNotFound(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("삭제된 아티스트는 404 다 — 패스가 있어도 그렇다")
    void deletedArtistIsNotFound() {
        grantPass(artistSeq, Instant.now().plus(30, ChronoUnit.DAYS), "ACTIVE");
        entityManager.createNativeQuery(
                        "update motif_core.artists set is_deleted = true where artist_seq = :seq")
                .setParameter("seq", artistSeq)
                .executeUpdate();
        entityManager.clear();

        assertNotFound(artistSeq);
    }

    // --- 픽스처 -------------------------------------------------------------

    private ArtistMotifPassResponse motifPass() {
        return artistMotifPassQueryService.motifPass(fanSeq, artistSeq);
    }

    private void assertNotFound(Integer artistSeq) {
        assertThatThrownBy(() -> artistMotifPassQueryService.motifPass(fanSeq, artistSeq))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ArtistErrorCode.ARTIST_NOT_FOUND);
    }

    private User newUser() {
        User user = userRepository.saveAndFlush(User.signUp());
        user.applyInitialNickname();
        return user;
    }

    private Integer newArtist(String motifBgImageKey) {
        User artistUser = userRepository.saveAndFlush(User.createArtist("카리나"));
        return artistRepository.saveAndFlush(Artist.register(
                        artistUser.getUserSeq(), null, newCompany(), null, motifBgImageKey, adminUserSeq))
                .getArtistSeq();
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

    private void price(short durationMonths, String amount) {
        price(artistSeq, durationMonths, amount);
    }

    private void price(Integer artistSeq, short durationMonths, String amount) {
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

    private void deactivatePrice(short durationMonths) {
        entityManager.createNativeQuery("""
                        update motif_core.motif_pass_prices set is_active = false
                         where artist_seq = :artistSeq and duration_months = :durationMonths
                        """)
                .setParameter("artistSeq", artistSeq)
                .setParameter("durationMonths", durationMonths)
                .executeUpdate();
        entityManager.clear();
    }

    /** 결제 흐름(§12)이 만들 행을 직접 넣는다 — 검증 대상은 조회 쪽이다. */
    private void grantPass(Integer artistSeq, Instant expiresDttm, String status) {
        grantPass(fanSeq, artistSeq, expiresDttm, status);
    }

    private void grantPass(Integer userSeq, Integer artistSeq, Instant expiresDttm, String status) {
        long orderSeq = newOrder(userSeq, artistSeq);
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

    private long newOrder(Integer userSeq, Integer artistSeq) {
        UUID orderUuid = UUID.randomUUID();
        entityManager.createNativeQuery("""
                        insert into motif_core.orders
                               (order_uuid, user_seq, product_type, artist_seq, country_code,
                                pass_duration_months, amount, currency, status,
                                payment_provider, payment_key, approved_dttm, expires_dttm)
                        values (cast(:orderUuid as uuid), :userSeq, 'PASS', :artistSeq, 'KR',
                                1, 9900.00, 'KRW', 'PAID',
                                'TEST', :paymentKey, current_timestamp,
                                current_timestamp + interval '1 day')
                        """)
                .setParameter("orderUuid", orderUuid.toString())
                .setParameter("userSeq", userSeq)
                .setParameter("artistSeq", artistSeq)
                .setParameter("paymentKey", orderUuid.toString())
                .executeUpdate();
        return ((Number) entityManager.createNativeQuery(
                        "select order_seq from motif_core.orders where order_uuid = cast(:orderUuid as uuid)")
                .setParameter("orderUuid", orderUuid.toString())
                .getSingleResult()).longValue();
    }
}
