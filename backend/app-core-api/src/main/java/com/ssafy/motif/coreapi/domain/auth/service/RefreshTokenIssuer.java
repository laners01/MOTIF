package com.ssafy.motif.coreapi.domain.auth.service;

import com.ssafy.motif.coreapi.domain.auth.jwt.AuthProperties;
import com.ssafy.motif.domain.core.auth.RefreshToken;
import com.ssafy.motif.domain.core.user.User;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * refresh token 생성·해싱과 발급 행 조립 — 로그인(§2.1)·재발급(§2.2) 공용.
 *
 * <p><b>형식 편차.</b> 명세 §2.1 응답 예시는 refresh token을 {@code eyJhbGciOi...}
 * (JWT 형태)로 적었지만, 여기서는 <b>불투명 랜덤 문자열</b>을 쓴다.
 * {@code refresh_tokens} 테이블이 원문 대신 {@code token_hash}만 갖는 구조
 * (DB SPEC §6)에서는 서버가 DB를 조회해야 유효성을 알 수 있어 JWT의 자기검증
 * 이점이 없고, 랜덤 문자열은 클레임 노출·키 회전 문제가 없다. 클라이언트는 받은
 * 값을 그대로 보관·재전송하므로 형식에 의존하지 않는다.
 *
 * <p><b>저장은 하지 않는다</b> — 리포지토리를 갖지 않고 엔티티까지만 만들어 준다.
 * 저장 시점은 호출자의 트랜잭션이 정해야 하고(로그인은 가입과, 재발급은 기존 행
 * 폐기와 같은 트랜잭션), 이 클래스는 DB 없이 단위 테스트할 수 있어야 한다.
 */
@Component
public class RefreshTokenIssuer {

    /** 256비트 — 추측 불가에 충분하고 CHAR(64) 해시 컬럼과 무관하게 길이가 고정된다. */
    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();
    private final Duration ttl;

    public RefreshTokenIssuer(AuthProperties properties) {
        this.ttl = properties.jwt().refreshTokenTtl();
    }

    /**
     * 발급 결과 — 원문은 클라이언트에게, 엔티티는 호출자가 저장한다.
     *
     * @param rawToken 응답에 실을 원문. 서버는 이 값을 저장하지 않는다
     * @param entity   {@code refresh_tokens} 에 INSERT 할 행(해시·만료 포함)
     */
    public record IssuedRefreshToken(String rawToken, RefreshToken entity) {
    }

    /** 새 refresh token 발급 — 원문 생성 + 해시·만료를 채운 행 조립. */
    public IssuedRefreshToken issueFor(User user) {
        String rawToken = generateToken();
        RefreshToken entity = RefreshToken.issue(user, hash(rawToken), Instant.now().plus(ttl));
        return new IssuedRefreshToken(rawToken, entity);
    }

    /** {@code refresh_tokens.expires_dttm} 산출용 만료 기간. */
    public Duration ttl() {
        return ttl;
    }

    /** 클라이언트에 내려줄 원문. 서버는 이 값을 저장하지 않는다. */
    public String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** {@code refresh_tokens.token_hash} — SHA-256 hex 소문자 64자(CHAR(64) 정확히 채움). */
    public String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256은 JDK 표준 — 도달 불가
            throw new IllegalStateException("SHA-256을 사용할 수 없다", e);
        }
    }
}
