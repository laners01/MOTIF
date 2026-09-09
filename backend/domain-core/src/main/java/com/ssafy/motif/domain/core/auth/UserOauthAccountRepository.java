package com.ssafy.motif.domain.core.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserOauthAccountRepository extends JpaRepository<UserOauthAccount, Integer> {

    /**
     * 재로그인 판정 — UNIQUE(provider_seq, oauth_subject) 조회.
     * 계정 상태·사용자 정보를 같은 왕복에서 쓰므로 fetch join 한다.
     */
    @Query("""
            select oa from UserOauthAccount oa
              join fetch oa.account a
              join fetch a.user
             where oa.provider.providerCode = :providerCode
               and oa.oauthSubject = :oauthSubject
            """)
    Optional<UserOauthAccount> findWithAccountAndUser(
            @Param("providerCode") String providerCode,
            @Param("oauthSubject") String oauthSubject);
}
