package com.ssafy.motif.coreapi.domain.user.service;

import com.ssafy.motif.coreapi.domain.user.dto.MotifPassItem;
import com.ssafy.motif.domain.core.artist.Artist;
import com.ssafy.motif.domain.core.artist.ArtistRepository;
import com.ssafy.motif.domain.core.pass.PassStatus;
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
 * §11.9 내 패스 일람을 실제 DB 에 대고 검증한다 (S15P21D104-70).
 *
 * <p><b>여기서 볼 것은 "거르지 않는다" 다.</b> 이 API 는 결제 이력이라 만료·환불된 패스와
 * <b>기간이 지난 {@code ACTIVE} 행</b>까지 그대로 내보낸다. 조회 하나로 끝나는 API 지만
 * 그 "그대로" 가 지켜지는지는 상태를 심어 봐야 안다 — 무심코 유효 패스만 주는 기존
 * 메서드({@code findActive})를 재사용하면 조용히 절반이 사라진다.
 *
 * <p>정렬(만료 늦은 순)도 여기서만 고정된다.
 *
 * <p><b>로컬 compose DB 는 세 레인이 공유한다</b> — 이 테스트는 자기가 만든 사용자로만
 * 조회한다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class MyMotifPassesLocalDbTest {

    @Autowired
    private UserMotifPassQueryService userMotifPassQueryService;
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
        artistSeq = newArtist("카리나");
    }

    // --- 거르지 않는다 -----------------------------------------------------------

    @Test
    @DisplayName("ACTIVE·EXPIRED·REFUNDED 가 모두 나온다 — 상태로 거르지 않는다")
    void everyStatusIsListed() {
        grantPass(artistSeq, future(30), PassStatus.ACTIVE);
        grantPass(newArtist("윈터"), past(10), PassStatus.EXPIRED);
        grantPass(newArtist("지젤"), future(20), PassStatus.REFUNDED);

        assertThat(passes())
                .extracting(MotifPassItem::status)
                .containsExactlyInAnyOrder(
                        PassStatus.ACTIVE, PassStatus.EXPIRED, PassStatus.REFUNDED);
    }

    @Test
    @DisplayName("기간이 지난 ACTIVE 행도 그대로 나온다 — 서버가 EXPIRED 로 바꾸지 않는다")
    void staleActiveRowIsReturnedAsIs() {
        Instant expired = past(1);
        grantPass(artistSeq, expired, PassStatus.ACTIVE);

        MotifPassItem item = passes().getFirst();

        assertThat(item.status()).isEqualTo(PassStatus.ACTIVE);
        assertThat(item.expiresDttm()).isBefore(Instant.now());
    }

    @Test
    @DisplayName("canceledDttm 이 있는 행도 그대로 나온다 — 이용 판정에 참여하지 않는다")
    void canceledPassIsListed() {
        grantPass(artistSeq, future(30), PassStatus.ACTIVE);
        setCanceled(artistSeq);

        MotifPassItem item = passes().getFirst();

        assertThat(item.canceledDttm()).isNotNull();
        assertThat(item.status()).isEqualTo(PassStatus.ACTIVE);
    }

    // --- 정렬 · 범위 --------------------------------------------------------------

    @Test
    @DisplayName("만료 늦은 순이다 — 유효한 패스가 위로 온다")
    void orderedByExpiryDesc() {
        Integer older = newArtist("윈터");
        Integer newer = newArtist("지젤");
        grantPass(older, past(5), PassStatus.EXPIRED);
        grantPass(artistSeq, future(10), PassStatus.ACTIVE);
        grantPass(newer, future(60), PassStatus.ACTIVE);

        assertThat(passes())
                .extracting(MotifPassItem::artistSeq)
                .containsExactly(newer, artistSeq, older);
    }

    @Test
    @DisplayName("남의 패스는 나오지 않는다")
    void passesOfAnotherUserAreHidden() {
        grantPass(newUser().getUserSeq(), artistSeq, future(30), PassStatus.ACTIVE);

        assertThat(passes()).isEmpty();
    }

    @Test
    @DisplayName("패스가 없으면 빈 배열이다 — 아티스트 조회도 부르지 않는다")
    void noPassYieldsEmptyList() {
        assertThat(passes()).isEmpty();
    }

    @Test
    @DisplayName("삭제된 아티스트의 패스도 나오고 이름이 붙는다 — 돈을 낸 기록이다")
    void passOfDeletedArtistSurvives() {
        grantPass(artistSeq, future(30), PassStatus.ACTIVE);
        entityManager.createNativeQuery(
                        "update motif_core.artists set is_deleted = true where artist_seq = :seq")
                .setParameter("seq", artistSeq)
                .executeUpdate();
        entityManager.clear();

        List<MotifPassItem> list = passes();

        assertThat(list).hasSize(1);
        assertThat(list.getFirst().artist().name()).isEqualTo("카리나");
    }

    // --- 항목 --------------------------------------------------------------------

    @Test
    @DisplayName("항목이 명세 형태 그대로다 — artistSeq 는 artist.artistSeq 와 같은 값")
    void itemCarriesEveryField() {
        Instant expires = future(30);
        grantPass(artistSeq, expires, PassStatus.ACTIVE);

        MotifPassItem item = passes().getFirst();

        assertThat(item.artistSeq()).isEqualTo(artistSeq);
        assertThat(item.artist().artistSeq()).isEqualTo(artistSeq);
        assertThat(item.artist().name()).isEqualTo("카리나");
        assertThat(item.artist().groupName()).isNull();
        assertThat(item.artist().profileImageUrl()).isNull();
        assertThat(item.durationMonths()).isEqualTo((short) 1);
        assertThat(item.startedDttm()).isNotNull();
        assertThat(item.expiresDttm()).isCloseTo(expires,
                org.assertj.core.api.Assertions.within(1, ChronoUnit.MILLIS));
    }

    // --- 픽스처 -------------------------------------------------------------

    private List<MotifPassItem> passes() {
        return userMotifPassQueryService.passes(fanSeq).list();
    }

    private static Instant future(int days) {
        return Instant.now().plus(days, ChronoUnit.DAYS);
    }

    private static Instant past(int days) {
        return Instant.now().minus(days, ChronoUnit.DAYS);
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

    private void grantPass(Integer artistSeq, Instant expiresDttm, PassStatus status) {
        grantPass(fanSeq, artistSeq, expiresDttm, status);
    }

    /** 결제 흐름(§12)이 만들 행을 직접 넣는다 — 검증 대상은 조회 쪽이다. */
    private void grantPass(Integer userSeq, Integer artistSeq, Instant expiresDttm,
                           PassStatus status) {
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
                .setParameter("status", status.name())
                .executeUpdate();
        entityManager.clear();
    }

    /** {@code ck_motif_passes_values} 가 {@code canceled_dttm >= started_dttm} 을 요구한다. */
    private void setCanceled(Integer artistSeq) {
        entityManager.createNativeQuery("""
                        update motif_core.motif_passes
                           set canceled_dttm = started_dttm + interval '1 day'
                         where user_seq = :userSeq and artist_seq = :artistSeq
                        """)
                .setParameter("userSeq", fanSeq)
                .setParameter("artistSeq", artistSeq)
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
