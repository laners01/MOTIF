package com.ssafy.motif.coreapi.domain.auth.service;

import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.domain.auth.AuthErrorCode;
import com.ssafy.motif.coreapi.domain.auth.dto.LoginResponse;
import com.ssafy.motif.coreapi.domain.auth.dto.UserSummaryResponse;
import com.ssafy.motif.coreapi.domain.auth.jwt.JwtProvider;
import com.ssafy.motif.coreapi.domain.auth.service.RefreshTokenIssuer.IssuedRefreshToken;
import com.ssafy.motif.coreapi.global.media.PublicMediaUrlResolver;
import com.ssafy.motif.domain.core.auth.RefreshToken;
import com.ssafy.motif.domain.core.auth.RefreshTokenRepository;
import com.ssafy.motif.domain.core.auth.UserAccount;
import com.ssafy.motif.domain.core.auth.UserAccountRepository;
import com.ssafy.motif.domain.core.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * refresh token rotation (명세 §2.2, DB SPEC v0.15 §6).
 *
 * <p>refresh token 은 <b>1회용</b>이다 — 재발급에 쓰인 토큰은 같은 트랜잭션에서 폐기하고
 * 새 행을 발급한다. 이미 폐기된 토큰이 다시 들어오면 <b>탈취 신호</b>로 보고 거부한다.
 *
 * <p>전 과정이 한 트랜잭션이며, 대상 행을 잠그고 시작한다
 * ({@code findByTokenHashForUpdate}) — 같은 토큰으로 동시에 두 요청이 오면 잠금이
 * 둘을 직렬화해 하나만 성공한다. 잠금이 없으면 둘 다 "미폐기"를 읽고 각자 새 토큰을
 * 발급해 1회용 계약이 깨진다.
 *
 * <p>실패 사유(미존재·만료·폐기)는 전부 같은 응답 코드로 나간다 — 구분해 주면
 * "이 토큰은 존재하지만 만료됐다"는 정보가 새어 나간다. 사유는 로그에만 남긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenRotator {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserAccountRepository userAccountRepository;
    private final RefreshTokenIssuer refreshTokenIssuer;
    private final JwtProvider jwtProvider;
    private final AccountStatusVerifier accountStatusVerifier;
    private final PublicMediaUrlResolver publicMediaUrlResolver;

    @Transactional
    public LoginResponse rotate(String rawRefreshToken) {
        RefreshToken stored = findAndLock(rawRefreshToken);
        verifyUsable(stored);

        UserAccount account = loadAccount(stored);
        accountStatusVerifier.verifyActive(account);
        User user = account.getUser();

        stored.useForRotation();

        String accessToken = jwtProvider.issueAccessToken(user);
        IssuedRefreshToken reissued = refreshTokenIssuer.issueFor(user);
        refreshTokenRepository.save(reissued.entity());

        return LoginResponse.of(
                accessToken,
                reissued.rawToken(),
                jwtProvider.accessTokenTtlSeconds(),
                // 재발급에는 가입 판정이 없다. 응답 형태를 §2.1 과 같게 유지하려고 필드는 남긴다
                false,
                UserSummaryResponse.of(user, publicMediaUrlResolver.resolveProfile(user.getProfileImageKey())));
    }

    private RefreshToken findAndLock(String rawRefreshToken) {
        return refreshTokenRepository.findByTokenHashForUpdate(refreshTokenIssuer.hash(rawRefreshToken))
                .orElseThrow(() -> new BusinessException(
                        AuthErrorCode.INVALID_REFRESH_TOKEN, "존재하지 않는 refresh token"));
    }

    private void verifyUsable(RefreshToken stored) {
        if (stored.isRevoked()) {
            // 폐기된 토큰의 재제출 = rotation 위반. 정상 클라이언트는 새 토큰으로 교체하므로 오지 않는다
            log.warn("refresh token 재사용 감지 — tokenSeq={} userSeq={}",
                    stored.getTokenSeq(), stored.getUser().getUserSeq());
            throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN, "폐기된 refresh token 재사용");
        }
        if (stored.isExpired(Instant.now())) {
            throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN, "만료된 refresh token");
        }
    }

    /**
     * 계정 상태 재검증용 조회 (명세 §1.5). 잠근 토큰 행에는 {@code user_seq} 만 있으므로
     * 사용자·계정은 여기서 따로 읽는다 — 잠금 범위를 {@code refresh_tokens} 로 좁히기 위한 분리다.
     */
    private UserAccount loadAccount(RefreshToken stored) {
        Integer userSeq = stored.getUser().getUserSeq();
        return userAccountRepository.findWithUserByUserSeq(userSeq)
                .orElseThrow(() -> new IllegalStateException(
                        "refresh token 의 사용자에게 user_accounts 행이 없다 — userSeq=" + userSeq));
    }
}
