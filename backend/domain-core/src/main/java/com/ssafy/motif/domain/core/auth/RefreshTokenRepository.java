package com.ssafy.motif.domain.core.auth;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
