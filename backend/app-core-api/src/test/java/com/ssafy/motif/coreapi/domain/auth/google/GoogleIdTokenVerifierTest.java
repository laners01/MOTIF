package com.ssafy.motif.coreapi.domain.auth.google;

import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.domain.auth.AuthErrorCode;
import com.ssafy.motif.coreapi.domain.auth.jwt.AuthProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleIdTokenVerifierTest {

    private static final String CLIENT_ID = "1234.apps.googleusercontent.com";

    private static GoogleIdTokenVerifier verifier(JwtDecoder decoder, String clientId) {
        return new GoogleIdTokenVerifier(decoder, new AuthProperties(
                new AuthProperties.Google(clientId),
                new AuthProperties.Jwt("secret-key-with-at-least-32-bytes!!!!", Duration.ofHours(1), Duration.ofDays(14))));
    }

    private static Jwt idToken(String issuer, String audience, String subject) {
        return Jwt.withTokenValue("id-token")
                .header("alg", "RS256")
                .claim("iss", issuer)
                .audience(List.of(audience))
                .subject(subject)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    @ParameterizedTest(name = "iss={0}")
    @ValueSource(strings = {"accounts.google.com", "https://accounts.google.com"})
    @DisplayName("구글이 쓰는 두 iss 형태를 모두 받아 sub 를 돌려준다")
    void acceptsBothIssuerForms(String issuer) {
        JwtDecoder decoder = token -> idToken(issuer, CLIENT_ID, "google-sub-1");

        assertThat(verifier(decoder, CLIENT_ID).verifyAndGetSubject("id-token"))
                .isEqualTo("google-sub-1");
    }

    @Test
    @DisplayName("다른 앱의 클라이언트 ID 로 발급된 토큰은 INVALID_GOOGLE_TOKEN")
    void rejectsForeignAudience() {
        JwtDecoder decoder = token -> idToken("accounts.google.com", "other-app.apps.googleusercontent.com", "sub");

        assertThatThrownBy(() -> verifier(decoder, CLIENT_ID).verifyAndGetSubject("id-token"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.INVALID_GOOGLE_TOKEN);
    }

    @Test
    @DisplayName("iss 가 구글이 아니면 INVALID_GOOGLE_TOKEN")
    void rejectsForeignIssuer() {
        JwtDecoder decoder = token -> idToken("https://evil.example.com", CLIENT_ID, "sub");

        assertThatThrownBy(() -> verifier(decoder, CLIENT_ID).verifyAndGetSubject("id-token"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.INVALID_GOOGLE_TOKEN);
    }

    @Test
    @DisplayName("서명·만료 검증 실패(디코더 예외)는 INVALID_GOOGLE_TOKEN 으로 바꾼다")
    void mapsDecoderFailure() {
        JwtDecoder decoder = token -> {
            throw new BadJwtException("서명 불일치");
        };

        assertThatThrownBy(() -> verifier(decoder, CLIENT_ID).verifyAndGetSubject("id-token"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.INVALID_GOOGLE_TOKEN);
    }

    @Test
    @DisplayName("sub 가 없으면 INVALID_GOOGLE_TOKEN")
    void rejectsMissingSubject() {
        JwtDecoder decoder = token -> Jwt.withTokenValue("id-token")
                .header("alg", "RS256")
                .claim("iss", "accounts.google.com")
                .audience(List.of(CLIENT_ID))
                .build();

        assertThatThrownBy(() -> verifier(decoder, CLIENT_ID).verifyAndGetSubject("id-token"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.INVALID_GOOGLE_TOKEN);
    }

    @Test
    @DisplayName("client-id 미설정은 토큰 문제가 아니라 설정 문제로 드러낸다")
    void failsFastOnMissingClientId() {
        JwtDecoder decoder = token -> idToken("accounts.google.com", CLIENT_ID, "sub");

        assertThatThrownBy(() -> verifier(decoder, "").verifyAndGetSubject("id-token"))
                .isInstanceOf(IllegalStateException.class);
    }
}
