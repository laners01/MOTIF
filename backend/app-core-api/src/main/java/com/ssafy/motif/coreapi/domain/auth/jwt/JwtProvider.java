package com.ssafy.motif.coreapi.domain.auth.jwt;

import com.ssafy.motif.common.error.CommonErrorCode;
import com.ssafy.motif.domain.core.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * 서비스 access token 발급·검증 (HS256).
 *
 * <p>어드민 JWT는 <b>서명키를 분리</b>한다(ADR-0009 D2) — 이 provider는 사용자
 * 토큰만 다루며, 어드민 키는 {@code /admin} 티켓에서 별도 빈으로 들어온다.
 */
@Slf4j
@Component
public class JwtProvider {

    private static final String CLAIM_ROLE = "role";

    private final SecretKey key;
    private final Duration accessTokenTtl;

    public JwtProvider(AuthProperties properties) {
        byte[] secret = properties.jwt().secret().getBytes(StandardCharsets.UTF_8);
        // HS256은 키가 256비트 이상이어야 한다 — 짧으면 여기서 즉시 기동 실패한다.
        this.key = Keys.hmacShaKeyFor(secret);
        this.accessTokenTtl = properties.jwt().accessTokenTtl();
    }

    /** 명세 §2.1 응답의 {@code expiresIn}(초). */
    public long accessTokenTtlSeconds() {
        return accessTokenTtl.toSeconds();
    }

    public String issueAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getUserSeq()))
                .claim(CLAIM_ROLE, user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenTtl)))
                .signWith(key)
                .compact();
    }

    /**
     * 서명·만료를 검증하고 claims를 돌려준다.
     *
     * @throws com.ssafy.motif.common.exception.BusinessException {@link CommonErrorCode#UNAUTHORIZED}
     */
    public Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            // 사유(만료·서명 불일치)는 응답에 싣지 않는다 — 로그에만 남긴다.
            log.debug("access token 검증 실패: {}", e.getMessage());
            throw new com.ssafy.motif.common.exception.BusinessException(
                    CommonErrorCode.UNAUTHORIZED, "access token 검증 실패: " + e.getMessage());
        }
    }
}
