package com.ssafy.motif.coreapi.auth.google;

import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.auth.AuthErrorCode;
import com.ssafy.motif.coreapi.auth.jwt.AuthProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Set;

/**
 * 구글 ID 토큰 검증 (명세 §2.1).
 *
 * <p>서명은 {@link JwtDecoder}(Nimbus + 구글 JWKS)가 검증하고 {@code exp}는
 * 기본 validator가 본다. 여기서는 <b>{@code iss}와 {@code aud}</b>를 추가로 확인한다 —
 * aud 검증이 빠지면 다른 앱의 클라이언트 ID로 발급된 토큰도 통과한다.
 *
 * <p>구글은 {@code iss}를 {@code accounts.google.com} 과
 * {@code https://accounts.google.com} 두 형태로 발급하므로 둘 다 허용한다.
 */
@Slf4j
@Component
public class GoogleIdTokenVerifier {

    private static final Set<String> ALLOWED_ISSUERS =
            Set.of("accounts.google.com", "https://accounts.google.com");

    private final JwtDecoder jwtDecoder;
    private final String clientId;

    public GoogleIdTokenVerifier(JwtDecoder googleIdTokenDecoder, AuthProperties properties) {
        this.jwtDecoder = googleIdTokenDecoder;
        this.clientId = properties.google().clientId();
    }

    /**
     * @return 구글 계정 식별자({@code sub}) — {@code user_oauth_accounts.oauth_subject}
     * @throws BusinessException {@link AuthErrorCode#INVALID_GOOGLE_TOKEN}
     */
    public String verifyAndGetSubject(String idToken) {
        if (!StringUtils.hasText(clientId)) {
            // 설정 누락을 토큰 위조로 보고할 수는 없다 — 운영자용 신호로 남긴다.
            log.error("motif.auth.google.client-id 가 비어 있어 구글 로그인을 처리할 수 없다");
            throw new IllegalStateException("motif.auth.google.client-id 미설정");
        }

        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(idToken);
        } catch (JwtException e) {
            log.debug("구글 ID 토큰 서명·만료 검증 실패: {}", e.getMessage());
            throw new BusinessException(AuthErrorCode.INVALID_GOOGLE_TOKEN, "구글 ID 토큰 검증 실패: " + e.getMessage());
        }

        // getIssuer()는 URL 파싱이라 스킴 없는 "accounts.google.com" 에서 예외가 난다.
        // 구글은 두 형태를 모두 발급하므로 원문 문자열로 비교한다.
        String issuer = jwt.getClaimAsString(JwtClaimNames.ISS);
        if (!ALLOWED_ISSUERS.contains(issuer)) {
            log.warn("구글 ID 토큰 iss 불일치: {}", issuer);
            throw new BusinessException(AuthErrorCode.INVALID_GOOGLE_TOKEN, "iss 불일치");
        }

        if (!jwt.getAudience().contains(clientId)) {
            log.warn("구글 ID 토큰 aud 불일치: {}", jwt.getAudience());
            throw new BusinessException(AuthErrorCode.INVALID_GOOGLE_TOKEN, "aud 불일치");
        }

        String subject = jwt.getSubject();
        if (!StringUtils.hasText(subject)) {
            throw new BusinessException(AuthErrorCode.INVALID_GOOGLE_TOKEN, "sub 없음");
        }
        return subject;
    }
}
