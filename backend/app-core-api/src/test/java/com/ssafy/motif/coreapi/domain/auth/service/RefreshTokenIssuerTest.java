package com.ssafy.motif.coreapi.domain.auth.service;

import com.ssafy.motif.coreapi.domain.auth.jwt.AuthProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenIssuerTest {

    private RefreshTokenIssuer issuer;

    @BeforeEach
    void setUp() {
        issuer = new RefreshTokenIssuer(new AuthProperties(
                new AuthProperties.Google("client-id"),
                new AuthProperties.Jwt("secret-key-with-at-least-32-bytes!!!!", Duration.ofHours(1), Duration.ofDays(14))));
    }

    @Test
    @DisplayName("해시는 CHAR(64) 를 정확히 채우는 소문자 hex 다")
    void hashFitsChar64() {
        String hash = issuer.hash(issuer.generateToken());

        assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("같은 토큰은 같은 해시로 — 재발급 조회(§2.2)가 성립해야 한다")
    void hashIsDeterministic() {
        String token = issuer.generateToken();

        assertThat(issuer.hash(token)).isEqualTo(issuer.hash(token));
    }

    @Test
    @DisplayName("발급 토큰은 매번 다르다 — UNIQUE(token_hash) 충돌이 나지 않아야 한다")
    void tokensAreUnique() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            tokens.add(issuer.generateToken());
        }

        assertThat(tokens).hasSize(1_000);
    }

    @Test
    @DisplayName("TTL 은 설정값을 그대로 쓴다")
    void ttlFromProperties() {
        assertThat(issuer.ttl()).isEqualTo(Duration.ofDays(14));
    }
}
