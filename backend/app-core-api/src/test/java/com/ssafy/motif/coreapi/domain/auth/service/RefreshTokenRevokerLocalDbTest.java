package com.ssafy.motif.coreapi.domain.auth.service;

import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.domain.auth.AuthErrorCode;
import com.ssafy.motif.coreapi.domain.auth.dto.LoginResponse;
import com.ssafy.motif.domain.core.auth.RefreshToken;
import com.ssafy.motif.domain.core.auth.RefreshTokenRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 명세 §2.3 로그아웃을 실제 DB에 대고 검증한다 (ADR-0003 격리 모드).
 *
 * <p>계약은 "내 토큰만 죽는다"와 "죽일 게 없어도 실패하지 않는다" 두 가지이고, 둘 다
 * {@code refresh_tokens} 행 상태로만 확인할 수 있어 DB가 필요하다. 소유자 판정 자체는
 * {@link RefreshTokenStateTest} 가 DB 없이 덮는다.
 *
 * <p>{@code @Transactional}로 매 테스트를 롤백한다. 실행 전
 * {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class RefreshTokenRevokerLocalDbTest {

    @Autowired
    private RefreshTokenRevoker revoker;
    @Autowired
    private RefreshTokenRotator rotator;
    @Autowired
    private GoogleLoginProcessor loginProcessor;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private RefreshTokenIssuer refreshTokenIssuer;

    private static String newSubject() {
        return "test-" + UUID.randomUUID();
    }

    private RefreshToken storedTokenOf(String rawToken) {
        return refreshTokenRepository.findByTokenHash(refreshTokenIssuer.hash(rawToken)).orElseThrow();
    }

    @Test
    @DisplayName("본인 토큰은 폐기된다")
    void ownTokenIsRevoked() {
        LoginResponse issued = loginProcessor.process(newSubject());

        revoker.revokeForLogout(issued.user().userSeq(), issued.refreshToken());

        assertThat(storedTokenOf(issued.refreshToken()).isRevoked()).isTrue();
    }

    @Test
    @DisplayName("타인 소유 토큰은 폐기되지 않고, 예외도 던지지 않는다 (명세 §2.3 기본안)")
    void otherUsersTokenIsUntouched() {
        LoginResponse victim = loginProcessor.process(newSubject());
        LoginResponse attacker = loginProcessor.process(newSubject());

        assertThatCode(() -> revoker.revokeForLogout(attacker.user().userSeq(), victim.refreshToken()))
                .doesNotThrowAnyException();

        assertThat(storedTokenOf(victim.refreshToken()).isRevoked()).isFalse();
    }

    @Test
    @DisplayName("존재하지 않는 토큰이어도 예외 없이 끝난다 (멱등)")
    void unknownTokenIsIgnored() {
        LoginResponse issued = loginProcessor.process(newSubject());

        assertThatCode(() -> revoker.revokeForLogout(
                issued.user().userSeq(), refreshTokenIssuer.generateToken()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("두 번 로그아웃해도 200 이고, 최초 폐기 시각을 덮어쓰지 않는다")
    void secondLogoutKeepsFirstRevokedTime() {
        LoginResponse issued = loginProcessor.process(newSubject());
        Integer userSeq = issued.user().userSeq();
        revoker.revokeForLogout(userSeq, issued.refreshToken());
        Instant firstRevokedAt = storedTokenOf(issued.refreshToken()).getRevokedDttm();

        assertThatCode(() -> revoker.revokeForLogout(userSeq, issued.refreshToken()))
                .doesNotThrowAnyException();

        assertThat(storedTokenOf(issued.refreshToken()).getRevokedDttm()).isEqualTo(firstRevokedAt);
    }

    @Test
    @DisplayName("다른 기기의 토큰은 살아 있다 — 현재 기기만 로그아웃 (DB SPEC §6)")
    void otherDeviceStaysLoggedIn() {
        String subject = newSubject();
        LoginResponse deviceA = loginProcessor.process(subject);
        LoginResponse deviceB = loginProcessor.process(subject);

        revoker.revokeForLogout(deviceA.user().userSeq(), deviceA.refreshToken());

        assertThat(storedTokenOf(deviceB.refreshToken()).isRevoked()).isFalse();
    }

    @Test
    @DisplayName("로그아웃한 토큰으로는 재발급되지 않는다 — 세션이 실제로 끊긴다 (§2.2 연계)")
    void revokedTokenCannotRefresh() {
        LoginResponse issued = loginProcessor.process(newSubject());
        revoker.revokeForLogout(issued.user().userSeq(), issued.refreshToken());

        assertThatThrownBy(() -> rotator.rotate(issued.refreshToken()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN);
    }
}
