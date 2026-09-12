package com.ssafy.motif.coreapi.domain.auth.service;

import com.ssafy.motif.domain.core.auth.RefreshToken;
import com.ssafy.motif.domain.core.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * refresh token 행의 상태 판정 (명세 §2.2). DB 없이 도는 계약이라 기본 {@code test} 태스크에 둔다.
 *
 * <p>rotation 전체 흐름은 {@code RefreshTokenRotatorLocalDbTest} 가 실제 DB로 검증한다.
 */
class RefreshTokenStateTest {

    private static final Instant NOW = Instant.parse("2026-09-10T00:00:00Z");

    private static RefreshToken tokenExpiringAt(Instant expiresDttm) {
        return RefreshToken.issue(User.signUp(), "hash", expiresDttm);
    }

    @Test
    @DisplayName("발급 직후에는 폐기 상태가 아니다")
    void freshTokenIsNotRevoked() {
        assertThat(tokenExpiringAt(NOW.plus(14, ChronoUnit.DAYS)).isRevoked()).isFalse();
    }

    @Test
    @DisplayName("재발급에 쓰면 사용 시각과 폐기 시각이 함께 남는다 (DB SPEC §6)")
    void rotationStampsUsedAndRevoked() {
        RefreshToken token = tokenExpiringAt(NOW.plus(14, ChronoUnit.DAYS));

        token.useForRotation();

        assertThat(token.isRevoked()).isTrue();
        assertThat(token.getLastUsedDttm()).isNotNull();
        assertThat(token.getRevokedDttm()).isEqualTo(token.getLastUsedDttm());
    }

    @Test
    @DisplayName("만료 경계 — expiresDttm == now 는 만료로 본다")
    void expiryBoundaryIsExpired() {
        assertThat(tokenExpiringAt(NOW).isExpired(NOW)).isTrue();
        assertThat(tokenExpiringAt(NOW.plusMillis(1)).isExpired(NOW)).isFalse();
        assertThat(tokenExpiringAt(NOW.minusMillis(1)).isExpired(NOW)).isTrue();
    }

    @Test
    @DisplayName("소유자 판정 — 본인 것만 참이다 (명세 §2.3 로그아웃)")
    void ownershipIsCheckedByUserSeq() {
        RefreshToken token = tokenOf(7);

        assertThat(token.isOwnedBy(7)).isTrue();
        assertThat(token.isOwnedBy(8)).isFalse();
    }

    @Test
    @DisplayName("소유자 판정 — null 은 소유로 보지 않는다")
    void nullIsNeverOwner() {
        assertThat(tokenOf(7).isOwnedBy(null)).isFalse();
        // 아직 저장되지 않아 userSeq 가 없는 엔티티
        assertThat(tokenExpiringAt(NOW).isOwnedBy(7)).isFalse();
    }

    /** {@code userSeq} 가 채워진(= 저장된 것처럼 보이는) 사용자의 토큰. */
    private static RefreshToken tokenOf(int userSeq) {
        User user = User.signUp();
        ReflectionTestUtils.setField(user, "userSeq", userSeq);
        return RefreshToken.issue(user, "hash", NOW.plus(14, ChronoUnit.DAYS));
    }
}
