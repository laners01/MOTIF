package com.ssafy.motif.coreapi.admin.pass;

import com.ssafy.motif.common.error.AdminErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.admin.pass.dto.PassPriceRequest;
import com.ssafy.motif.coreapi.admin.pass.dto.PassPriceResponse;
import com.ssafy.motif.coreapi.admin.security.AuthenticatedAdmin;
import com.ssafy.motif.coreapi.domain.artist.ArtistErrorCode;
import com.ssafy.motif.domain.core.pass.MotifPassPriceRepository;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * §3.9.1·§3.9.2 를 실제 DB에 대고 검증한다 (S15P21D104-569).
 *
 * <p>확인하는 것은 세 가지다 — ① upsert 가 UNIQUE 축마다 한 행만 유지하는가
 * ② 가격 갱신이 기존 ACTIVE 패스를 건드리지 않는가(소급 없음) ③ 활성 패스가 있어도
 * 비활성화를 <b>막지 않고</b> 경고 카운트만 돌려주는가.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class AdminPassPriceServiceLocalDbTest {

    @Autowired
    private AdminPassPriceService adminPassPriceService;
    @Autowired
    private MotifPassPriceRepository passPriceRepository;
    @Autowired
    private UserRepository userRepository;
    @PersistenceContext
    private EntityManager entityManager;

    private AuthenticatedAdmin admin;
    private Integer artistSeq;

    @BeforeEach
    void setUp() {
        admin = new AuthenticatedAdmin(1, newUser().getUserSeq(), "운영자");
        artistSeq = newArtist();
    }

    @Test
    @DisplayName("신규 조합은 INSERT, 같은 조합 재요청은 같은 행 UPDATE")
    void upsertKeepsOneRowPerAxis() {
        PassPriceResponse created = adminPassPriceService.upsert(admin, artistSeq, "KR", (short) 1,
                new PassPriceRequest(new BigDecimal("9900.00"), "KRW", true));

        PassPriceResponse updated = adminPassPriceService.upsert(admin, artistSeq, "KR", (short) 1,
                new PassPriceRequest(new BigDecimal("12900.00"), "KRW", true));
        entityManager.flush();

        assertThat(updated.passPriceSeq()).isEqualTo(created.passPriceSeq());
        assertThat(updated.amount()).isEqualByComparingTo("12900.00");
        assertThat(passPriceRepository.findByArtistSeqOrderByCountrySeqAscDurationMonthsAsc(artistSeq))
                .hasSize(1);
    }

    @Test
    @DisplayName("개월 수가 다르면 별개의 행이다 — 가격표는 국가×개월 축이다")
    void durationIsPartOfTheKey() {
        adminPassPriceService.upsert(admin, artistSeq, "KR", (short) 1,
                new PassPriceRequest(new BigDecimal("9900.00"), "KRW", true));
        adminPassPriceService.upsert(admin, artistSeq, "KR", (short) 12,
                new PassPriceRequest(new BigDecimal("99000.00"), "KRW", true));
        entityManager.flush();

        assertThat(adminPassPriceService.list(artistSeq).list())
                .extracting(PassPriceResponse::durationMonths)
                .containsExactly((short) 1, (short) 12);
    }

    @Test
    @DisplayName("목록은 설정된 행만 준다 — 미설정 조합은 화면이 빈 칸으로 그린다")
    void listReturnsOnlyConfiguredRows() {
        assertThat(adminPassPriceService.list(artistSeq).list()).isEmpty();
    }

    @Test
    @DisplayName("활성 패스가 있어도 비활성화를 막지 않고 경고 카운트를 돌려준다")
    void deactivationIsWarnedNotBlocked() {
        adminPassPriceService.upsert(admin, artistSeq, "KR", (short) 1,
                new PassPriceRequest(new BigDecimal("9900.00"), "KRW", true));
        newActivePass((short) 1);

        PassPriceResponse off = adminPassPriceService.upsert(admin, artistSeq, "KR", (short) 1,
                new PassPriceRequest(new BigDecimal("9900.00"), "KRW", false));

        assertThat(off.isActive()).isFalse();
        assertThat(off.activePassCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("가격을 올려도 기존 ACTIVE 패스의 주문 금액은 그대로다 — 소급 적용 없음")
    void existingPassKeepsItsSnapshot() {
        adminPassPriceService.upsert(admin, artistSeq, "KR", (short) 1,
                new PassPriceRequest(new BigDecimal("9900.00"), "KRW", true));
        long orderSeq = newActivePass((short) 1);

        adminPassPriceService.upsert(admin, artistSeq, "KR", (short) 1,
                new PassPriceRequest(new BigDecimal("19900.00"), "KRW", true));
        entityManager.flush();

        assertThat((BigDecimal) entityManager.createNativeQuery(
                        "select amount from motif_core.orders where order_seq = :seq")
                .setParameter("seq", orderSeq)
                .getSingleResult())
                .isEqualByComparingTo("9900.00");
    }

    @Test
    @DisplayName("삭제된 아티스트는 404 — 가격을 만들지 않는다")
    void deletedArtistIsRejected() {
        entityManager.createNativeQuery(
                        "update motif_core.artists set is_deleted = true where artist_seq = :seq")
                .setParameter("seq", artistSeq)
                .executeUpdate();
        entityManager.clear();

        assertThatThrownBy(() -> adminPassPriceService.upsert(admin, artistSeq, "KR", (short) 1,
                new PassPriceRequest(new BigDecimal("9900.00"), "KRW", true)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ArtistErrorCode.ARTIST_NOT_FOUND);
    }

    @Test
    @DisplayName("미존재 국가는 404 COUNTRY_NOT_FOUND")
    void unknownCountryIsRejected() {
        assertThatThrownBy(() -> adminPassPriceService.upsert(admin, artistSeq, "JP", (short) 1,
                new PassPriceRequest(new BigDecimal("9900.00"), "JPY", true)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AdminErrorCode.COUNTRY_NOT_FOUND);
    }

    // --- 픽스처 -------------------------------------------------------------

    /** {@code orders} → {@code motif_passes} 를 만들어 경고 카운트가 셀 대상을 둔다. */
    private long newActivePass(short durationMonths) {
        Integer buyerSeq = newUser().getUserSeq();
        long orderSeq = ((Number) entityManager.createNativeQuery("""
                        insert into motif_core.orders
                            (order_uuid, user_seq, product_type, artist_seq, country_code,
                             pass_duration_months, amount, currency, status, expires_dttm)
                        values (gen_random_uuid(), :userSeq, 'PASS', :artistSeq, 'KR',
                                :months, 9900.00, 'KRW', 'PENDING', now() + interval '30 minutes')
                        returning order_seq
                        """)
                .setParameter("userSeq", buyerSeq)
                .setParameter("artistSeq", artistSeq)
                .setParameter("months", durationMonths)
                .getSingleResult()).longValue();

        entityManager.createNativeQuery("""
                        insert into motif_core.motif_passes
                            (user_seq, artist_seq, order_seq, duration_months, started_dttm, expires_dttm, status)
                        values (:userSeq, :artistSeq, :orderSeq, :months, now(),
                                now() + interval '30 days', 'ACTIVE')
                        """)
                .setParameter("userSeq", buyerSeq)
                .setParameter("artistSeq", artistSeq)
                .setParameter("orderSeq", orderSeq)
                .setParameter("months", durationMonths)
                .executeUpdate();
        return orderSeq;
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
