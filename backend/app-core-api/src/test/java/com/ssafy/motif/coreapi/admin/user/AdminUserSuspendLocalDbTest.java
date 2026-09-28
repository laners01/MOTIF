package com.ssafy.motif.coreapi.admin.user;

import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.admin.security.AuthenticatedAdmin;
import com.ssafy.motif.coreapi.admin.user.dto.UserSuspendResponse;
import com.ssafy.motif.coreapi.domain.auth.service.AccountStatusVerifier;
import com.ssafy.motif.domain.core.auth.AccountStatus;
import com.ssafy.motif.domain.core.auth.RefreshToken;
import com.ssafy.motif.domain.core.auth.RefreshTokenRepository;
import com.ssafy.motif.domain.core.auth.UserAccount;
import com.ssafy.motif.domain.core.auth.UserAccountRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * §3.14.3~4 정지·해제를 실제 DB에 대고 검증한다 (S15P21D104-582).
 *
 * <p>핵심 셋 — ① 상태 전이와 토큰 폐기가 <b>한 트랜잭션</b>에서 같이 살아남는가
 * (벌크 UPDATE 의 {@code clearAutomatically} 가 계정 변경을 버리면 여기서 깨진다)
 * ② 재진입이 실제로 막히는가({@link AccountStatusVerifier} 가 403 을 던지는가)
 * ③ 상태 충돌 409 두 방향.
 *
 * <p><b>이벤트는 검증하지 않는다.</b> 발행이 {@code AFTER_COMMIT} 인데 이 테스트는
 * 롤백되므로 콜백 자체가 돌지 않는다 — 발행 여부는 소비자가 생기는 583 에서 본다.
 *
 * <p><b>로컬 DB 는 여러 세션이 공유한다.</b> 단언은 이 테스트가 만든 seq 로만 좁힌다.
 *
 * <p>실행: {@code docker compose up -d --wait} 후 {@code ./gradlew :app-core-api:localDbTest}.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class AdminUserSuspendLocalDbTest {

    @Autowired
    private AdminUserCommandService adminUserCommandService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserAccountRepository userAccountRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private AccountStatusVerifier accountStatusVerifier;
    @PersistenceContext
    private EntityManager entityManager;

    private AuthenticatedAdmin admin;

    @BeforeEach
    void setUp() {
        admin = new AuthenticatedAdmin(1, newUser().getUserSeq(), "운영자");
    }

    @Test
    @DisplayName("정지하면 계정 상태와 토큰 폐기가 함께 남는다 — 한쪽만 반영되지 않는다")
    void suspendPersistsBothStatusAndRevocation() {
        User user = newUser();
        UserAccount account = userAccountRepository.saveAndFlush(UserAccount.activeFor(user));
        newToken(user);
        newToken(user);

        UserSuspendResponse response = adminUserCommandService.suspend(admin, user.getUserSeq(), "욕설 반복");
        entityManager.flush();
        entityManager.clear();

        assertThat(response.accountStatus()).isEqualTo(AccountStatus.SUSPENDED);
        assertThat(response.revokedTokenCount()).isEqualTo(2);
        // 벌크 UPDATE 가 영속성 컨텍스트를 비우므로 계정 변경이 살아남았는지 DB 에서 다시 읽는다
        assertThat(userAccountRepository.findById(account.getAccountSeq()).orElseThrow().isSuspended())
                .isTrue();
        assertThat(activeTokenCount(user.getUserSeq())).isZero();
    }

    @Test
    @DisplayName("정지 후 로그인·재발급 판정이 403 ACCOUNT_SUSPENDED 로 막힌다")
    void suspendedAccountCannotReenter() {
        User user = newUser();
        userAccountRepository.saveAndFlush(UserAccount.activeFor(user));

        adminUserCommandService.suspend(admin, user.getUserSeq(), "욕설 반복");
        entityManager.flush();
        entityManager.clear();

        UserAccount reloaded = userAccountRepository.findWithUserByUserSeq(user.getUserSeq()).orElseThrow();
        assertThatThrownBy(() -> accountStatusVerifier.verifyActive(reloaded))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("정지된 계정");
    }

    @Test
    @DisplayName("이미 폐기된 토큰은 revokedTokenCount 에 세지 않는다 — 최초 폐기 시각을 지키려고")
    void alreadyRevokedTokenIsNotCounted() {
        User user = newUser();
        userAccountRepository.saveAndFlush(UserAccount.activeFor(user));
        RefreshToken revoked = newToken(user);
        revoked.revoke();
        Instant firstRevokedAt = revoked.getRevokedDttm();
        newToken(user);
        entityManager.flush();

        UserSuspendResponse response = adminUserCommandService.suspend(admin, user.getUserSeq(), "욕설 반복");
        entityManager.flush();
        entityManager.clear();

        assertThat(response.revokedTokenCount()).isEqualTo(1);
        assertThat(refreshTokenRepository.findById(revoked.getTokenSeq()).orElseThrow().getRevokedDttm())
                .isEqualTo(firstRevokedAt);
    }

    @Test
    @DisplayName("이미 정지된 회원을 또 정지하면 409")
    void suspendTwiceConflicts() {
        User user = newUser();
        userAccountRepository.saveAndFlush(UserAccount.activeFor(user));
        adminUserCommandService.suspend(admin, user.getUserSeq(), "욕설 반복");

        assertThatThrownBy(() -> adminUserCommandService.suspend(admin, user.getUserSeq(), "또"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("이미 정지된 회원");
    }

    @Test
    @DisplayName("해제하면 ACTIVE 로 돌아가되 토큰은 복구되지 않는다")
    void unsuspendDoesNotRestoreTokens() {
        User user = newUser();
        userAccountRepository.saveAndFlush(UserAccount.activeFor(user));
        newToken(user);
        adminUserCommandService.suspend(admin, user.getUserSeq(), "욕설 반복");

        assertThat(adminUserCommandService.unsuspend(admin, user.getUserSeq()).accountStatus())
                .isEqualTo(AccountStatus.ACTIVE);
        entityManager.flush();
        entityManager.clear();
        assertThat(activeTokenCount(user.getUserSeq())).isZero();
    }

    @Test
    @DisplayName("정지 상태가 아닌 회원을 해제하면 409")
    void unsuspendActiveConflicts() {
        User user = newUser();
        userAccountRepository.saveAndFlush(UserAccount.activeFor(user));

        assertThatThrownBy(() -> adminUserCommandService.unsuspend(admin, user.getUserSeq()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("정지 상태가 아닌 회원");
    }

    @Test
    @DisplayName("아티스트는 정지 대상이 아니다 — 400")
    void artistCannotBeSuspended() {
        User artist = userRepository.saveAndFlush(User.createArtist("t-" + UUID.randomUUID()));

        assertThatThrownBy(() -> adminUserCommandService.suspend(admin, artist.getUserSeq(), "사유"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("정지 대상이 아닌 역할");
    }

    @Test
    @DisplayName("없는 회원을 정지하면 404")
    void suspendMissingUser() {
        assertThatThrownBy(() -> adminUserCommandService.suspend(admin, -1, "사유"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("존재하지 않는 사용자");
    }

    private long activeTokenCount(Integer userSeq) {
        return ((Number) entityManager.createNativeQuery("""
                        select count(*) from motif_core.refresh_tokens
                         where user_seq = :seq and revoked_dttm is null
                        """)
                .setParameter("seq", userSeq)
                .getSingleResult()).longValue();
    }

    private User newUser() {
        User user = userRepository.saveAndFlush(User.signUp());
        user.applyInitialNickname();
        return userRepository.saveAndFlush(user);
    }

    private RefreshToken newToken(User user) {
        return refreshTokenRepository.saveAndFlush(RefreshToken.issue(
                user,
                UUID.randomUUID().toString().replace("-", ""),
                Instant.now().plus(14, ChronoUnit.DAYS)));
    }
}
