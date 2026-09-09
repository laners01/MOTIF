package com.ssafy.motif.coreapi.auth;

import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.auth.dto.LoginResponse;
import com.ssafy.motif.domain.core.auth.AccountStatus;
import com.ssafy.motif.domain.core.auth.RefreshToken;
import com.ssafy.motif.domain.core.auth.RefreshTokenRepository;
import com.ssafy.motif.domain.core.auth.UserAccount;
import com.ssafy.motif.domain.core.auth.UserOauthAccount;
import com.ssafy.motif.domain.core.auth.UserOauthAccountRepository;
import com.ssafy.motif.domain.core.user.User;
import com.ssafy.motif.domain.core.user.UserRepository;
import com.ssafy.motif.domain.core.user.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 명세 §2.1 가입·재로그인 경로를 실제 DB에 대고 검증한다 (ADR-0003 격리 모드).
 *
 * <p>구글 왕복은 {@link GoogleLoginProcessor}의 관심사가 아니다 — 검증이 끝난
 * {@code oauthSubject}부터 시작하므로 토큰을 흉내낼 필요가 없다.
 *
 * <p>{@code @Transactional}로 매 테스트를 롤백한다. 실행 전
 * {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class GoogleLoginProcessorLocalDbTest {

    @Autowired
    private GoogleLoginProcessor processor;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserOauthAccountRepository userOauthAccountRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private RefreshTokenIssuer refreshTokenIssuer;

    private static String newSubject() {
        return "test-" + UUID.randomUUID();
    }

    @Test
    @DisplayName("최초 로그인 — isNewUser=true, 3개 테이블에 각 1행, 닉네임 user_{seq}")
    void firstLoginSignsUp() {
        String subject = newSubject();
        long usersBefore = userRepository.count();

        LoginResponse response = processor.process(subject);

        assertThat(response.isNewUser()).isTrue();
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(3600);
        assertThat(userRepository.count()).isEqualTo(usersBefore + 1);

        Integer userSeq = response.user().userSeq();
        assertThat(response.user().nickname()).isEqualTo("user_" + userSeq);
        assertThat(response.user().profileImageUrl()).isNull();

        Optional<UserOauthAccount> linked =
                userOauthAccountRepository.findWithAccountAndUser("GOOGLE", subject);
        assertThat(linked).isPresent();
        UserAccount account = linked.get().getAccount();
        assertThat(account.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getUser().getUserSeq()).isEqualTo(userSeq);
        assertThat(account.getUser().getRole()).isEqualTo(UserRole.USER);
        assertThat(account.getUser().isDeleted()).isFalse();
    }

    @Test
    @DisplayName("같은 구글 계정 재로그인 — isNewUser=false, users 행이 늘지 않는다")
    void secondLoginReusesAccount() {
        String subject = newSubject();
        LoginResponse first = processor.process(subject);
        long usersAfterSignUp = userRepository.count();

        LoginResponse second = processor.process(subject);

        assertThat(second.isNewUser()).isFalse();
        assertThat(second.user().userSeq()).isEqualTo(first.user().userSeq());
        assertThat(userRepository.count()).isEqualTo(usersAfterSignUp);
    }

    @Test
    @DisplayName("refresh token 원문은 저장되지 않는다 — token_hash 만 남는다")
    void refreshTokenIsStoredHashedOnly() {
        LoginResponse response = processor.process(newSubject());
        String issued = response.refreshToken();

        // 원문으로는 찾을 수 없고, 해시로는 찾힌다
        assertThat(refreshTokenRepository.findByTokenHash(issued)).isEmpty();
        Optional<RefreshToken> stored = refreshTokenRepository.findByTokenHash(refreshTokenIssuer.hash(issued));
        assertThat(stored).isPresent();
        assertThat(stored.get().getTokenHash()).hasSize(64).isNotEqualTo(issued);
        assertThat(stored.get().getRevokedDttm()).isNull();
        assertThat(stored.get().getExpiresDttm()).isAfter(stored.get().getRegDttm());
    }

    @Test
    @DisplayName("매 로그인마다 새 refresh token 행이 쌓인다 (기기별 — §6)")
    void eachLoginIssuesNewRefreshToken() {
        String subject = newSubject();
        LoginResponse first = processor.process(subject);
        LoginResponse second = processor.process(subject);

        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
        List<String> hashes = List.of(
                refreshTokenIssuer.hash(first.refreshToken()),
                refreshTokenIssuer.hash(second.refreshToken()));
        assertThat(hashes).allSatisfy(h -> assertThat(refreshTokenRepository.findByTokenHash(h)).isPresent());
    }

    @Test
    @DisplayName("정지 계정은 403 ACCOUNT_SUSPENDED (명세 §1.5)")
    void suspendedAccountIsRejected() {
        String subject = newSubject();
        processor.process(subject);
        UserAccount account = userOauthAccountRepository
                .findWithAccountAndUser("GOOGLE", subject).orElseThrow().getAccount();
        ReflectionTestUtils.setField(account, "accountStatus", AccountStatus.SUSPENDED);

        assertThatThrownBy(() -> processor.process(subject))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.ACCOUNT_SUSPENDED);
    }

    @Test
    @DisplayName("삭제 사용자는 403 ACCOUNT_DELETED (명세 §1.5)")
    void deletedUserIsRejected() {
        String subject = newSubject();
        processor.process(subject);
        User user = userOauthAccountRepository
                .findWithAccountAndUser("GOOGLE", subject).orElseThrow().getAccount().getUser();
        ReflectionTestUtils.setField(user, "deleted", true);

        assertThatThrownBy(() -> processor.process(subject))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.ACCOUNT_DELETED);
    }

    @Test
    @DisplayName("가입 3행이 실제로 커밋 가능한 형태다 — flush 로 제약 위반이 없음을 확인")
    void signUpRowsSatisfyConstraints() {
        processor.process(newSubject());

        // 롤백 전 flush — CHECK·FK·UNIQUE 위반이 있으면 여기서 터진다
        TestTransaction.flagForRollback();
        assertThat(TestTransaction.isActive()).isTrue();
        userRepository.flush();
    }
}
