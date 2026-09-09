package com.ssafy.motif.coreapi.domain.auth.service;

import com.ssafy.motif.coreapi.domain.auth.dto.LoginResponse;
import com.ssafy.motif.coreapi.domain.auth.google.GoogleIdTokenVerifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 인증 유스케이스 (명세 §2.1). 토큰 재발급·로그아웃(§2.2·§2.3)은 후속 티켓이다.
 *
 * <p><b>트랜잭션 밖</b>이다 — 구글 서버 왕복(JWKS·검증)을 DB 트랜잭션 안에 두지
 * 않고, 동시 최초 로그인 충돌 시 새 트랜잭션으로 재시도하기 위한 경계다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final GoogleIdTokenVerifier googleIdTokenVerifier;
    private final GoogleLoginProcessor googleLoginProcessor;

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
}
