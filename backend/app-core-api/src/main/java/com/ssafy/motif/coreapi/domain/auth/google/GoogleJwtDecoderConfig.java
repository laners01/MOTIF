package com.ssafy.motif.coreapi.domain.auth.google;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * 구글 ID 토큰 디코더.
 *
 * <p>JWKS를 게으르게 받아 캐시하고 키 롤링을 따라간다 — 우리가 공개키를 들고
 * 있지 않는다. {@code google-api-client} 대신 이 경로를 쓴 이유는
 * {@code app-core-api/build.gradle} 주석 참조 (ADR-0009 근거 2, 램 제약).
 */
@Configuration
public class GoogleJwtDecoderConfig {

    /** 구글 공개키 세트 — 엔드포인트는 구글 OpenID 설정 문서의 고정 값이다. */
    private static final String GOOGLE_JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs";

    @Bean
    public JwtDecoder googleIdTokenDecoder() {
        return NimbusJwtDecoder.withJwkSetUri(GOOGLE_JWK_SET_URI).build();
    }
}
