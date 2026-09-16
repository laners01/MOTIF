package com.ssafy.motif.coreapi.domain.auth.service;

import com.ssafy.motif.domain.core.auth.RefreshToken;
import com.ssafy.motif.domain.core.auth.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * 로그아웃 — 현재 기기의 refresh token 폐기 (명세 §2.3, DB SPEC v0.15 §6
 * "일반 로그아웃은 현재 기기의 refresh token 만 revoke").
 *
 * <p><b>멱등하다.</b> 폐기할 대상이 없어도 실패시키지 않는다 — 로그아웃은 재시도·중복
 * 호출이 흔하고, 실패를 알려 봐야 클라이언트가 할 수 있는 일이 없다. 게다가 "그 토큰은
 * 없다/남의 것이다"를 구분해 주면 토큰 존재 여부가 새어 나간다(명세 §2.3 기본안).
 *
 * <p><b>행을 잠그지 않는다.</b> §2.2 rotation 과 달리 동시 실행이 계약을 깨지 않는다 —
 * 두 요청이 겹쳐도 같은 값으로 폐기할 뿐이다.
 *
 * <p>access token 은 여기서 무효화되지 않는다(JWT 라 서버 상태가 없다). 클라이언트가
 * 로컬에서 버려야 하며, FCM 디바이스 토큰 비활성화도 클라이언트가 §14.2 로 따로 호출한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenRevoker {

    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenIssuer refreshTokenIssuer;

    @Transactional
    public void revokeForLogout(Integer userSeq, String rawRefreshToken) {
        Optional<RefreshToken> found =
                refreshTokenRepository.findByTokenHash(refreshTokenIssuer.hash(rawRefreshToken));

        if (found.isEmpty()) {
            log.debug("로그아웃 — 알 수 없는 refresh token (무시): userSeq={}", userSeq);
            return;
        }

        RefreshToken token = found.get();
        if (!token.isOwnedBy(userSeq)) {
            // 남의 토큰을 폐기해 주면 임의 사용자를 로그아웃시킬 수 있다. 거부 대신 무시한다 —
            // 403 을 주면 "그 토큰이 존재한다"는 사실이 드러난다
            log.warn("로그아웃 — 타인 소유 refresh token 제출 (무시): userSeq={} tokenSeq={}",
                    userSeq, token.getTokenSeq());
            return;
        }
        if (token.isRevoked()) {
            // 최초 폐기 시각이 감사에 더 유용하므로 덮어쓰지 않는다
            log.debug("로그아웃 — 이미 폐기된 토큰 (무시): tokenSeq={}", token.getTokenSeq());
            return;
        }

        token.revoke();
    }

    /**
     * 이 사용자의 모든 기기 로그아웃 — 회원 정지 (어드민 명세 §3.14.3).
     *
     * <p>로그아웃(§2.3)이 "현재 기기 하나"인 것과 정반대다. 정지는 계정 전체를 막는
     * 것이라 기기 하나만 남아도 재발급으로 서비스를 계속 쓰게 된다.
     *
     * <p><b>호출부의 트랜잭션에 참여한다.</b> 상태 전이와 토큰 폐기가 한 트랜잭션이어야
     * "정지는 됐는데 토큰은 살아 있는" 중간 상태가 생기지 않는다.
     *
     * <p>이미 발급된 access token 은 만료까지 유효하다 — JWT 라 서버 상태가 없다.
     * 명세 §1.5 가 수용한 동작이고, 매 요청마다 계정 조회를 붙이지 않기 위한 선택이다.
     *
     * @return 이번 호출로 폐기된 토큰 수 (§3.14.3 {@code revokedTokenCount})
     */
    @Transactional
    public int revokeAll(Integer userSeq) {
        int revoked = refreshTokenRepository.revokeAllByUserSeq(userSeq, Instant.now());
        log.info("전체 기기 로그아웃(정지): userSeq={} revokedTokenCount={}", userSeq, revoked);
        return revoked;
    }
}
