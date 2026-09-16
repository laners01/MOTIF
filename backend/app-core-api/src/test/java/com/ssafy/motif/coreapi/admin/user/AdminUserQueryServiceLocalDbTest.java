package com.ssafy.motif.coreapi.admin.user;

import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.admin.user.dto.UserDetailResponse;
import com.ssafy.motif.coreapi.admin.user.dto.UserSummaryResponse;
import com.ssafy.motif.common.response.PageResponse;
import com.ssafy.motif.domain.core.auth.AccountStatus;
import com.ssafy.motif.domain.core.auth.UserAccount;
import com.ssafy.motif.domain.core.auth.UserAccountRepository;
import com.ssafy.motif.domain.core.user.User;
import com.ssafy.motif.domain.core.user.UserRepository;
import com.ssafy.motif.domain.core.user.UserRole;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * §3.14.1~2 를 실제 DB에 대고 검증한다 (S15P21D104-581).
 *
 * <p><b>이 테스트가 없으면 잡히지 않는 것이 있다.</b> 통계 6종은 네이티브 SQL 이라
 * 컬럼명·스키마 접두어가 컴파일에도 기본 {@code test} 태스크에도 걸리지 않는다.
 * 실행해 봐야 안다 — 그래서 값이 0 이어도 {@link #statsQueryRuns} 를 남긴다.
 *
 * <p><b>로컬 DB 는 여러 세션이 공유한다.</b> 단언은 이 테스트가 만든 seq 로만 좁힌다 —
 * 전체 건수에 기대면 남의 데이터로 깨진다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait},
 * 그다음 {@code ./gradlew :app-core-api:localDbTest}.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class AdminUserQueryServiceLocalDbTest {

    @Autowired
    private AdminUserQueryService adminUserQueryService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserAccountRepository userAccountRepository;
    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("닉네임 부분 일치로 찾고, 역할·계정상태 필터가 걸린다")
    void searchFilters() {
        User user = newUser();
        userAccountRepository.saveAndFlush(UserAccount.activeFor(user));
        String nickname = user.getNickname();

        assertThat(seqsOf(adminUserQueryService.list(nickname, null, null, null, 0, 20)))
                .containsExactly(user.getUserSeq());
        assertThat(seqsOf(adminUserQueryService.list(nickname, null, UserRole.USER, null, 0, 20)))
                .containsExactly(user.getUserSeq());
        assertThat(seqsOf(adminUserQueryService.list(nickname, null, UserRole.ARTIST, null, 0, 20)))
                .isEmpty();
        assertThat(seqsOf(adminUserQueryService.list(nickname, null, null, AccountStatus.ACTIVE, 0, 20)))
                .containsExactly(user.getUserSeq());
        assertThat(seqsOf(adminUserQueryService.list(nickname, null, null, AccountStatus.SUSPENDED, 0, 20)))
                .isEmpty();
    }

    @Test
    @DisplayName("검색어가 없어도 전체가 조회된다 — null 을 like 에 넘기지 않는다")
    void blankQueryListsAll() {
        User user = newUser();
        entityManager.flush();

        assertThat(adminUserQueryService.list(null, user.getUserSeq(), null, null, 0, 20).list())
                .extracting(UserSummaryResponse::userSeq)
                .containsExactly(user.getUserSeq());
    }

    @Test
    @DisplayName("삭제된 회원도 어드민 목록에 나온다 — isDeleted 로 구분한다")
    void deletedUserIsListed() {
        User user = newUser();
        entityManager.flush();
        execute("update motif_core.users set is_deleted = true where user_seq = :seq", user.getUserSeq());
        entityManager.clear();

        assertThat(adminUserQueryService.list(null, user.getUserSeq(), null, null, 0, 20).list())
                .singleElement()
                .satisfies(row -> assertThat(row.isDeleted()).isTrue());
    }

    @Test
    @DisplayName("없는 회원 상세는 404 USER_NOT_FOUND")
    void detailOfMissingUser() {
        assertThatThrownBy(() -> adminUserQueryService.detail(-1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("userSeq=-1");
    }

    @Test
    @DisplayName("통계 네이티브 쿼리가 실제로 돈다 — 신규 회원은 전부 0")
    void statsQueryRuns() {
        User user = newUser();
        entityManager.flush();

        UserDetailResponse.Stats stats = adminUserQueryService.detail(user.getUserSeq()).stats();

        assertThat(stats).isEqualTo(new UserDetailResponse.Stats(0, 0, 0, 0, 0, 0));
    }

    @Test
    @DisplayName("기간이 지난 ACTIVE 패스는 보유 패스에서 빠진다 — 만료 배치가 아직 없다")
    void expiredPassIsExcluded() {
        User user = newUser();
        entityManager.flush();
        Integer artistSeq = newArtist();
        newPass(user.getUserSeq(), artistSeq, Instant.now().minus(1, ChronoUnit.DAYS));

        UserDetailResponse detail = adminUserQueryService.detail(user.getUserSeq());
        assertThat(detail.activePasses()).isEmpty();
        assertThat(detail.stats().activePassCount()).isZero();
    }

    @Test
    @DisplayName("유효 패스는 아티스트 이름과 함께 나오고 건수가 목록 크기와 같다")
    void activePassIsListed() {
        User user = newUser();
        entityManager.flush();
        Integer artistSeq = newArtist();
        newPass(user.getUserSeq(), artistSeq, Instant.now().plus(30, ChronoUnit.DAYS));

        UserDetailResponse detail = adminUserQueryService.detail(user.getUserSeq());
        assertThat(detail.activePasses()).singleElement()
                .satisfies(pass -> {
                    assertThat(pass.artistSeq()).isEqualTo(artistSeq);
                    assertThat(pass.artistName()).isNotBlank();
                });
        assertThat(detail.stats().activePassCount()).isEqualTo(detail.activePasses().size());
    }

    private static java.util.List<Integer> seqsOf(PageResponse<UserSummaryResponse> page) {
        return page.list().stream().map(UserSummaryResponse::userSeq).toList();
    }

    private User newUser() {
        User user = userRepository.saveAndFlush(User.signUp());
        user.applyInitialNickname();
        return userRepository.saveAndFlush(user);
    }

    /** 아티스트는 {@code users(ARTIST)} + {@code artists} 두 행이다 — 이름은 users 쪽이다. */
    private Integer newArtist() {
        User artistUser = userRepository.saveAndFlush(User.createArtist("t-" + shortUuid()));
        String companyName = "t-" + shortUuid();
        entityManager.createNativeQuery(
                        "insert into motif_core.companies (company_name, reg_usr_seq) values (:name, :usr)")
                .setParameter("name", companyName)
                .setParameter("usr", artistUser.getUserSeq())
                .executeUpdate();
        Number companySeq = (Number) entityManager.createNativeQuery(
                        "select company_seq from motif_core.companies where company_name = :name")
                .setParameter("name", companyName)
                .getSingleResult();
        entityManager.createNativeQuery("""
                        insert into motif_core.artists (user_seq, company_seq, reg_usr_seq)
                        values (:usr, :company, :usr)
                        """)
                .setParameter("usr", artistUser.getUserSeq())
                .setParameter("company", companySeq.intValue())
                .executeUpdate();
        return ((Number) entityManager.createNativeQuery(
                        "select artist_seq from motif_core.artists where user_seq = :usr")
                .setParameter("usr", artistUser.getUserSeq())
                .getSingleResult()).intValue();
    }

    /**
     * 패스 1건 — {@code motif_passes.order_seq} 가 FK 라 주문 행이 먼저다.
     *
     * <p>주문은 {@code PENDING} 으로 만든다. {@code PAID} 는 결제 수단·승인 시각까지
     * 요구하는데(ck_orders_status_shape) 여기서 볼 것은 패스의 유효 판정이다.
     */
    private void newPass(Integer userSeq, Integer artistSeq, Instant expiresDttm) {
        UUID orderUuid = UUID.randomUUID();
        entityManager.createNativeQuery("""
                        insert into motif_core.orders
                               (order_uuid, user_seq, product_type, artist_seq, country_code,
                                pass_duration_months, amount, currency, status, expires_dttm)
                        values (:uuid, :usr, 'PASS', :artist, 'KR', 1, 1000, 'KRW', 'PENDING',
                                current_timestamp + interval '1 hour')
                        """)
                .setParameter("uuid", orderUuid)
                .setParameter("usr", userSeq)
                .setParameter("artist", artistSeq)
                .executeUpdate();
        entityManager.createNativeQuery("""
                        insert into motif_core.motif_passes
                               (user_seq, artist_seq, order_seq, duration_months,
                                started_dttm, expires_dttm, status)
                        select :usr, :artist, o.order_seq, 1,
                               :expires - interval '30 days', :expires, 'ACTIVE'
                          from motif_core.orders o
                         where o.order_uuid = :uuid
                        """)
                .setParameter("usr", userSeq)
                .setParameter("artist", artistSeq)
                .setParameter("expires", expiresDttm)
                .setParameter("uuid", orderUuid)
                .executeUpdate();
    }

    private void execute(String sql, Integer seq) {
        entityManager.createNativeQuery(sql).setParameter("seq", seq).executeUpdate();
    }

    private static String shortUuid() {
        return UUID.randomUUID().toString().substring(0, 20);
    }
}
