package com.ssafy.motif.coreapi.domain.auth.jwt;

import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.domain.auth.AuthErrorCode;
import com.ssafy.motif.domain.core.user.User;
import com.ssafy.motif.domain.core.user.UserRole;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtProviderTest {

    private static final String SECRET = "test-only-secret-key-with-at-least-32-bytes!!";

    private static JwtProvider provider(Duration accessTtl) {
        return new JwtProvider(new AuthProperties(
                new AuthProperties.Google("client-id"),
                new AuthProperties.Jwt(SECRET, accessTtl, Duration.ofDays(14))));
    }

    /** 테스트용 User — 엔티티 생성자가 protected/private 이라 리플렉션으로 seq·role 을 채운다. */
    private static User user(int seq, UserRole role) {
        User user = ReflectionTestUtils.invokeMethod(User.class, "signUp");
        ReflectionTestUtils.setField(user, "userSeq", seq);
        ReflectionTestUtils.setField(user, "role", role);
        return user;
    }

    @Test
    @DisplayName("발급한 access token 은 sub=userSeq, role 클레임을 갖는다")
    void issueAndParse() {
        JwtProvider provider = provider(Duration.ofHours(1));

        Claims claims = provider.parse(provider.issueAccessToken(user(42, UserRole.USER)));

        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get("role", String.class)).isEqualTo("USER");
    }

    @Test
    @DisplayName("expiresIn 은 설정한 access token TTL 의 초 값이다 (명세 §2.1)")
    void accessTokenTtlSeconds() {
        assertThat(provider(Duration.ofHours(1)).accessTokenTtlSeconds()).isEqualTo(3600);
    }

    @Test
    @DisplayName("서명이 다른 키로 만든 토큰은 UNAUTHORIZED 로 거부한다")
    void rejectsForeignSignature() {
        String foreign = new JwtProvider(new AuthProperties(
                new AuthProperties.Google("client-id"),
                new AuthProperties.Jwt("another-secret-key-with-at-least-32-bytes!!", Duration.ofHours(1), Duration.ofDays(14))))
                .issueAccessToken(user(1, UserRole.USER));

        assertThatThrownBy(() -> provider(Duration.ofHours(1)).parse(foreign))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("만료된 토큰은 UNAUTHORIZED 로 거부한다")
    void rejectsExpired() {
        JwtProvider expiring = provider(Duration.ofSeconds(-1));
        String expired = expiring.issueAccessToken(user(1, UserRole.USER));

        assertThatThrownBy(() -> expiring.parse(expired))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("변조된 토큰은 UNAUTHORIZED 로 거부한다")
    void rejectsTampered() {
        JwtProvider provider = provider(Duration.ofHours(1));
        String token = provider.issueAccessToken(user(1, UserRole.USER));
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThatThrownBy(() -> provider.parse(tampered))
                .isInstanceOf(BusinessException.class);
    }
}
