package com.ssafy.motif.coreapi.auth.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 인증 설정 (프로파일별 값은 {@code application-{local,local-remote,prod}.yml}).
 *
 * @param google 구글 OAuth — {@code clientId}는 ID 토큰의 {@code aud} 검증에 쓴다.
 *               <b>비어 있으면 로그인 요청이 거부된다</b>(기동은 된다 — 인증과
 *               무관한 기능 개발을 막지 않기 위한 선택).
 * @param jwt    서비스 토큰 — {@code secret}은 HS256 키(32바이트 이상 필요).
 */
@ConfigurationProperties(prefix = "motif.auth")
public record AuthProperties(Google google, Jwt jwt) {

    public record Google(String clientId) {
    }

    public record Jwt(String secret, Duration accessTokenTtl, Duration refreshTokenTtl) {
    }
}
