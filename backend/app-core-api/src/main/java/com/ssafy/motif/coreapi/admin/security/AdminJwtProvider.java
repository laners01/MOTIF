package com.ssafy.motif.coreapi.admin.security;

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
 * 어드민 JWT 발급·검증 (명세 §1.2) — 팬 앱 토큰과 <b>서명키가 다르다</b>.
 *
 * <p>키를 나눈 것이 교차 사용 차단의 1선이다. 팬 토큰을 어드민 경로에 내면 여기서
 * 서명 검증이 깨지고, 어드민 토큰을 사용자 경로에 내면 {@code JwtProvider} 에서 깨진다.
 * {@code typ=ADMIN} 클레임은 키가 실수로 같아졌을 때를 위한 2선이다.
 *
 * <p>클레임: {@code sub=adminSeq} · {@code usr=userSeq}(감사 주체) · {@code name} ·
 * {@code typ=ADMIN} · {@code exp}. refresh 는 없다 — 만료되면 재로그인이다(§1.2).
 */
@Slf4j
@Component
public class AdminJwtProvider {

    /** 교차 사용 차단용 타입 클레임 (§1.2). */
    static final String CLAIM_TYPE = "typ";
    static final String TYPE_ADMIN = "ADMIN";
    private static final String CLAIM_USER_SEQ = "usr";
    private static final String CLAIM_NAME = "name";

    private final SecretKey key;
    private final Duration ttl;

    public AdminJwtProvider(AdminProperties properties) {
        // HS256 은 키가 256비트 이상이어야 한다 — 짧으면 여기서 즉시 기동 실패한다.
        this.key = Keys.hmacShaKeyFor(properties.jwt().secret().getBytes(StandardCharsets.UTF_8));
        this.ttl = properties.jwt().ttl();
    }

    /** 명세 3.1.1 응답의 {@code expiresIn}(초). */
    public long ttlSeconds() {
        return ttl.toSeconds();
    }

    public String issue(AuthenticatedAdmin admin) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(admin.adminSeq()))
                .claim(CLAIM_USER_SEQ, admin.userSeq())
                .claim(CLAIM_NAME, admin.name())
                .claim(CLAIM_TYPE, TYPE_ADMIN)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    /**
     * 서명·만료·{@code typ} 을 검증한다.
     *
     * @return 실패하면 {@code null} — 사유는 응답에 싣지 않는다(로그만).
     *         판정은 호출부가 아니라 인가 필터가 한다({@link AdminAuthFilter}).
     */
    public AuthenticatedAdmin parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
            if (!TYPE_ADMIN.equals(claims.get(CLAIM_TYPE, String.class))) {
                log.debug("어드민 토큰이 아니다: typ={}", claims.get(CLAIM_TYPE, String.class));
                return null;
            }
            return new AuthenticatedAdmin(
                    Integer.parseInt(claims.getSubject()),
                    claims.get(CLAIM_USER_SEQ, Integer.class),
                    claims.get(CLAIM_NAME, String.class));
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            log.debug("어드민 토큰 검증 실패: {}", e.getMessage());
            return null;
        }
    }
}
