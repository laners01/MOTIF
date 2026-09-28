package com.ssafy.motif.domain.core.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Integer> {

    /**
     * 계정 상태 재검증용 조회 (명세 §1.5 — 로그인·재발급 시점 모두 검증).
     * 상태와 사용자 정보를 같은 왕복에서 쓰므로 fetch join 한다.
     *
     * <p>{@code uq_user_accounts_user} 로 user 당 1행이라 결과는 최대 하나다.
     */
    @Query("select a from UserAccount a join fetch a.user u where u.userSeq = :userSeq")
    Optional<UserAccount> findWithUserByUserSeq(@Param("userSeq") Integer userSeq);
}
