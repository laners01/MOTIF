package com.ssafy.motif.coreapi.domain.auth.service;

import com.ssafy.motif.common.error.CommonErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.domain.auth.AuthErrorCode;
import com.ssafy.motif.coreapi.domain.auth.dto.LoginResponse;
import com.ssafy.motif.domain.core.auth.AccountStatus;
import com.ssafy.motif.domain.core.auth.RefreshToken;
import com.ssafy.motif.domain.core.auth.RefreshTokenRepository;
import com.ssafy.motif.domain.core.auth.UserAccount;
import com.ssafy.motif.domain.core.auth.UserOauthAccountRepository;
import com.ssafy.motif.domain.core.user.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 명세 §2.2 rotation 을 실제 DB에 대고 검증한다 (ADR-0003 격리 모드).
 *
 * <p>rotation 의 계약은 "쓴 토큰은 죽는다"와 "죽은 토큰은 다시 못 쓴다" 두 가지이고,
 * 둘 다 {@code refresh_tokens} 행 상태로만 확인할 수 있어 DB가 필요하다.
 *
 * <p>{@code @Transactional}로 매 테스트를 롤백한다. 실행 전
 * {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class RefreshTokenRotatorLocalDbTest {

    @Autowired
    private RefreshTokenRotator rotator;
    @Autowired
    private GoogleLoginProcessor loginProcessor;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private RefreshTokenIssuer refreshTokenIssuer;
    @Autowired
    private UserOauthAccountRepository userOauthAccountRepository;
    @PersistenceContext
    private EntityManager entityManager;

    private static String newSubject() {
        return "test-" + UUID.randomUUID();
    }

    /** 로그인으로 사용자 1명과 refresh token 1개를 만들어 준다. */
    private LoginResponse login(String subject) {
        return loginProcessor.process(subject);
    }

    private RefreshToken storedTokenOf(String rawToken) {
        return refreshTokenRepository.findByTokenHash(refreshTokenIssuer.hash(rawToken)).orElseThrow();
    }

    @Test
    @DisplayName("재발급 성공 — 새 토큰 쌍이 나오고 응답 형태는 §2.1 과 같다")
    void rotateIssuesNewPair() {
        LoginResponse issued = login(newSubject());

        LoginResponse refreshed = rotator.rotate(issued.refreshToken());

        assertThat(refreshed.refreshToken()).isNotEqualTo(issued.refreshToken());
        assertThat(refreshed.tokenType()).isEqualTo("Bearer");
        assertThat(refreshed.expiresIn()).isEqualTo(3600);
        assertThat(refreshed.user().userSeq()).isEqualTo(issued.user().userSeq());
        // 재발급에는 가입 판정이 없다 — 필드는 유지하되 항상 false (기본안)
        assertThat(refreshed.isNewUser()).isFalse();
    }

    @Test
    @DisplayName("쓴 토큰은 폐기되고 last_used_dttm 이 남는다 (DB SPEC §6)")
    void usedTokenIsRevokedAndStamped() {
        LoginResponse issued = login(newSubject());

        rotator.rotate(issued.refreshToken());

        RefreshToken old = storedTokenOf(issued.refreshToken());
        assertThat(old.getRevokedDttm()).isNotNull();
        assertThat(old.getLastUsedDttm()).isNotNull();
        assertThat(old.isRevoked()).isTrue();
    }

    @Test
    @DisplayName("새 행이 추가된다 — 기존 행을 덮어쓰지 않는다")
    void rotateInsertsNewRow() {
        LoginResponse issued = login(newSubject());
        long before = refreshTokenRepository.count();

        LoginResponse refreshed = rotator.rotate(issued.refreshToken());

        assertThat(refreshTokenRepository.count()).isEqualTo(before + 1);
        RefreshToken fresh = storedTokenOf(refreshed.refreshToken());
        assertThat(fresh.getRevokedDttm()).isNull();
        assertThat(fresh.getExpiresDttm()).isAfter(Instant.now());
    }

    @Test
    @DisplayName("같은 토큰을 두 번 쓰면 두 번째는 401 — 재사용 감지 (명세 §2.2)")
    void reusedTokenIsRejected() {
        LoginResponse issued = login(newSubject());
        rotator.rotate(issued.refreshToken());

        assertThatThrownBy(() -> rotator.rotate(issued.refreshToken()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    @DisplayName("만료된 토큰은 401")
    void expiredTokenIsRejected() {
        LoginResponse issued = login(newSubject());
        expireInDatabase(storedTokenOf(issued.refreshToken()).getTokenSeq());

        assertThatThrownBy(() -> rotator.rotate(issued.refreshToken()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }

    /**
     * 만료된 행을 만든다.
     *
     * <p>엔티티의 {@code expiresDttm} 만 과거로 바꾸면 안 된다 —
     * {@code ck_refresh_tokens_expires(expires_dttm > reg_dttm)} 때문에 flush 에서 제약
     * 위반으로 터진다. {@code reg_dttm} 은 {@code updatable=false} 라 JPA 로는 함께 옮길 수
     * 없으므로, 두 컬럼을 한 번에 과거로 미는 네이티브 UPDATE 를 쓴다.
     */
    private void expireInDatabase(Long tokenSeq) {
        entityManager.flush();
        entityManager.createNativeQuery("""
                        update motif_core.refresh_tokens
                           set reg_dttm = :regDttm, expires_dttm = :expiresDttm
                         where token_seq = :tokenSeq
                        """)
                .setParameter("regDttm", Instant.now().minus(30, ChronoUnit.DAYS))
                .setParameter("expiresDttm", Instant.now().minus(1, ChronoUnit.DAYS))
                .setParameter("tokenSeq", tokenSeq)
                .executeUpdate();
        // 영속성 컨텍스트의 옛 상태를 버려 rotate 가 갱신된 행을 읽게 한다
        entityManager.clear();
    }

    @Test
    @DisplayName("존재하지 않는 토큰은 401 — 만료·폐기와 같은 응답이다")
    void unknownTokenIsRejected() {
        assertThatThrownBy(() -> rotator.rotate(refreshTokenIssuer.generateToken()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    @DisplayName("정지 계정은 403 ACCOUNT_SUSPENDED — 재발급 시점에도 검증한다 (명세 §1.5)")
    void suspendedAccountIsRejected() {
        String subject = newSubject();
        LoginResponse issued = login(subject);
        UserAccount account = userOauthAccountRepository
                .findWithAccountAndUser("GOOGLE", subject).orElseThrow().getAccount();
        ReflectionTestUtils.setField(account, "accountStatus", AccountStatus.SUSPENDED);

        assertThatThrownBy(() -> rotator.rotate(issued.refreshToken()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.ACCOUNT_SUSPENDED);
    }

    @Test
    @DisplayName("삭제 사용자는 403 ACCOUNT_DELETED (명세 §1.5)")
    void deletedUserIsRejected() {
        String subject = newSubject();
        LoginResponse issued = login(subject);
        User user = userOauthAccountRepository
                .findWithAccountAndUser("GOOGLE", subject).orElseThrow().getAccount().getUser();
        ReflectionTestUtils.setField(user, "deleted", true);

        assertThatThrownBy(() -> rotator.rotate(issued.refreshToken()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.ACCOUNT_DELETED);
    }

    @Test
    @DisplayName("다른 기기의 토큰은 영향받지 않는다 — 기기별 행 (DB SPEC §6)")
    void otherDeviceTokenSurvives() {
        String subject = newSubject();
        LoginResponse deviceA = login(subject);
        LoginResponse deviceB = login(subject);

        rotator.rotate(deviceA.refreshToken());

        assertThat(storedTokenOf(deviceB.refreshToken()).isRevoked()).isFalse();
    }
}
