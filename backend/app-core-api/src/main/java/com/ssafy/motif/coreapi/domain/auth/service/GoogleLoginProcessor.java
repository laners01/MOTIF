package com.ssafy.motif.coreapi.domain.auth.service;

import com.ssafy.motif.coreapi.domain.auth.dto.LoginResponse;
import com.ssafy.motif.coreapi.domain.auth.dto.UserSummaryResponse;
import com.ssafy.motif.coreapi.domain.auth.jwt.JwtProvider;
import com.ssafy.motif.coreapi.domain.auth.service.RefreshTokenIssuer.IssuedRefreshToken;
import com.ssafy.motif.coreapi.global.media.PublicMediaUrlResolver;
import com.ssafy.motif.domain.core.auth.OauthProvider;
import com.ssafy.motif.domain.core.auth.OauthProviderRepository;
import com.ssafy.motif.domain.core.auth.RefreshTokenRepository;
import com.ssafy.motif.domain.core.auth.UserAccount;
import com.ssafy.motif.domain.core.auth.UserAccountRepository;
import com.ssafy.motif.domain.core.auth.UserOauthAccount;
import com.ssafy.motif.domain.core.auth.UserOauthAccountRepository;
import com.ssafy.motif.domain.core.user.User;
import com.ssafy.motif.domain.core.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 구글 로그인의 DB 작업 — 조회·가입·토큰 발급을 <b>한 트랜잭션</b>으로 묶는다.
 *
 * <p>{@link AuthService}와 분리한 이유: 동시 최초 로그인이 UNIQUE 충돌을 내면
 * 그 트랜잭션은 이미 롤백 대상이라 같은 트랜잭션 안에서 재조회할 수 없다.
 * 재시도는 트랜잭션 밖({@link AuthService})에서 이 메서드를 다시 호출해야 한다.
 */
@Component
@RequiredArgsConstructor
public class GoogleLoginProcessor {

    /** {@code oauth_providers} 시드 값 (changeSet 0013-01). */
    private static final String PROVIDER_GOOGLE = "GOOGLE";

    private final UserRepository userRepository;
    private final UserAccountRepository userAccountRepository;
    private final UserOauthAccountRepository userOauthAccountRepository;
    private final OauthProviderRepository oauthProviderRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProvider jwtProvider;
    private final RefreshTokenIssuer refreshTokenIssuer;
    private final AccountStatusVerifier accountStatusVerifier;
    private final PublicMediaUrlResolver publicMediaUrlResolver;

    @Transactional
    public LoginResponse process(String oauthSubject) {
        Optional<UserOauthAccount> linked =
                userOauthAccountRepository.findWithAccountAndUser(PROVIDER_GOOGLE, oauthSubject);

        boolean isNewUser = linked.isEmpty();
        UserAccount account = linked.map(UserOauthAccount::getAccount)
                .orElseGet(() -> signUp(oauthSubject));
        User user = account.getUser();

        accountStatusVerifier.verifyActive(account);

        String accessToken = jwtProvider.issueAccessToken(user);
        IssuedRefreshToken refreshToken = refreshTokenIssuer.issueFor(user);
        refreshTokenRepository.save(refreshToken.entity());

        return LoginResponse.of(
                accessToken,
                refreshToken.rawToken(),
                jwtProvider.accessTokenTtlSeconds(),
                isNewUser,
                UserSummaryResponse.of(user, publicMediaUrlResolver.resolveProfile(user.getProfileImageKey())));
    }

    /** users → user_accounts → user_oauth_accounts 3행 생성 (명세 §2.1). */
    private UserAccount signUp(String oauthSubject) {
        User user = User.signUp();
        // IDENTITY 라 seq는 INSERT 이후에만 알 수 있다. flush로 확보한 뒤 user_{seq}로 교체한다.
        // 이 교체는 UPDATE 로 나가므로 신규 가입 행의 mod_dttm 이 채워진다 —
        // "mod_dttm NULL = 수정된 적 없음" 규칙의 예외이며 리뷰 확인 대상이다.
        userRepository.saveAndFlush(user);
        user.applyInitialNickname();

        UserAccount account = userAccountRepository.save(UserAccount.activeFor(user));

        OauthProvider provider = oauthProviderRepository.findByProviderCode(PROVIDER_GOOGLE)
                .orElseThrow(() -> new IllegalStateException(
                        "oauth_providers 에 " + PROVIDER_GOOGLE + " 행이 없다 — 시드 changeSet 0013-01 확인"));
        userOauthAccountRepository.save(UserOauthAccount.link(account, provider, oauthSubject));

        return account;
    }
}
