package com.ssafy.motif.domain.core.auth;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * 재발급(rotation) 대상 조회 — <b>행을 잠근다</b>({@code SELECT ... FOR UPDATE},
     * DB SPEC §6 "사용 시 해당 행을 잠그고").
     *
     * <p>같은 refresh token 으로 동시에 두 요청이 들어오면 이 잠금이 둘을 직렬화한다.
     * 잠금이 없으면 두 요청이 같은 행을 각각 "미폐기"로 읽고 둘 다 새 토큰을 발급해,
     * 1회용이라는 계약이 깨진다.
     *
     * <p><b>{@code user} 를 fetch join 하지 않는다</b> — 조인하면 {@code FOR UPDATE} 가
     * {@code users} 행까지 잠가 잠금 범위가 필요 이상으로 넓어진다. 사용자 정보는
     * 같은 트랜잭션에서 따로 조회한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select rt from RefreshToken rt where rt.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    /**
     * 이 사용자의 미폐기 refresh token 을 전부 폐기한다 — 회원 정지 (어드민 명세 §3.14.3).
     *
     * <p><b>행을 꺼내 오지 않는다.</b> 로그아웃(§2.3)은 대상이 한 행이라 엔티티를 읽어
     * {@code revoke()} 를 부르지만, 정지는 기기 수만큼이고 전부 같은 값으로 바꾼다 —
     * 벌크 UPDATE 한 방이 왕복도 적고 잠금 범위도 좁다.
     *
     * <p><b>{@code revoked_dttm IS NULL} 을 조건에 둔다.</b> 이미 폐기된 행을 덮어쓰면
     * 최초 폐기 시각이 사라진다(감사에 그쪽이 더 유용하다 — {@code RefreshTokenRevoker}
     * 와 같은 근거). 반환값이 "이번에 실제로 꺼진 수"가 되는 근거이기도 하다.
     *
     * <p><b>{@code flushAutomatically} 가 빠지면 정지가 사라진다.</b> 호출부는 바로 앞에서
     * {@code UserAccount.suspend()} 로 다른 테이블을 더럽혀 둔 상태다. Hibernate 의 auto-flush
     * 는 쿼리가 건드리는 테이블({@code refresh_tokens})만 보므로 {@code user_accounts} 의
     * 변경을 내보내지 않고, 뒤이은 {@code clearAutomatically} 가 그 변경을 그대로 버린다 —
     * 토큰은 폐기됐는데 계정은 ACTIVE 인 상태로 커밋된다. 둘은 같이 켜야 한다.
     *
     * @return 이번 호출로 폐기된 행 수 — 응답의 {@code revokedTokenCount}
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update RefreshToken rt
               set rt.revokedDttm = :now
             where rt.user.userSeq = :userSeq
               and rt.revokedDttm is null
            """)
    int revokeAllByUserSeq(@Param("userSeq") Integer userSeq, @Param("now") Instant now);
}
