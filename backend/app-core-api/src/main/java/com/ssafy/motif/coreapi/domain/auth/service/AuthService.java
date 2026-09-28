package com.ssafy.motif.coreapi.domain.auth.service;

import com.ssafy.motif.coreapi.domain.auth.dto.LoginResponse;
import com.ssafy.motif.coreapi.domain.auth.google.GoogleIdTokenVerifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 인증 유스케이스 (명세 §2.1·§2.2). 로그아웃(§2.3)은 후속 티켓이다.
 *
 * <p><b>트랜잭션 밖</b>이다 — 구글 서버 왕복(JWKS·검증)을 DB 트랜잭션 안에 두지
 * 않고, 동시 최초 로그인 충돌 시 새 트랜잭션으로 재시도하기 위한 경계다.
 * DB 작업은 {@link GoogleLoginProcessor}·{@link RefreshTokenRotator} 가 각자의
 * 트랜잭션에서 처리한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final GoogleIdTokenVerifier googleIdTokenVerifier;
    private final GoogleLoginProcessor googleLoginProcessor;
    private final RefreshTokenRotator refreshTokenRotator;
    private final RefreshTokenRevoker refreshTokenRevoker;

    public LoginResponse loginWithGoogle(String idToken) {
        String oauthSubject = googleIdTokenVerifier.verifyAndGetSubject(idToken);
        try {
            return googleLoginProcessor.process(oauthSubject);
        } catch (DataIntegrityViolationException e) {
            // 같은 구글 계정의 최초 로그인이 동시에 들어와 uq_user_oauth_subject 가 충돌했다.
            // 다른 요청이 이미 가입을 끝냈으므로, 새 트랜잭션에서 재조회하면 기존 회원 경로로 흐른다.
            log.info("동시 최초 로그인 충돌 — 재시도로 처리한다: {}", e.getMessage());
            return googleLoginProcessor.process(oauthSubject);
        }
    }

    /**
     * 토큰 재발급 (명세 §2.2). 외부 왕복이 없어 rotation 트랜잭션에 그대로 위임한다.
     */
    public LoginResponse refresh(String refreshToken) {
        return refreshTokenRotator.rotate(refreshToken);
    }

    /**
     * 로그아웃 (명세 §2.3) — 현재 기기의 refresh token 만 폐기한다. 멱등이라 폐기할
     * 대상이 없어도 예외를 던지지 않는다.
     *
     * @param userSeq 인증된 요청자. 남의 토큰을 폐기하지 못하도록 소유자 판정에 쓴다
     */
    public void logout(Integer userSeq, String refreshToken) {
        refreshTokenRevoker.revokeForLogout(userSeq, refreshToken);
    }
}
