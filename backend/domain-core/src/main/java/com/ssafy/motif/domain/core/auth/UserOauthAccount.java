package com.ssafy.motif.domain.core.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 서비스 계정 ↔ OAuth 제공자 계정 연결 (DB SPEC v0.15 §4, changeSet 0002-03).
 *
 * <p>{@code oauthSubject}는 구글 ID 토큰의 {@code sub}다. 재로그인 판정은
 * UNIQUE(uq_user_oauth_subject) {@code (provider_seq, oauth_subject)}로 한다.
 *
 * <p><b>BaseTimeEntity를 상속하지 않는다</b> — {@code mod_dttm} 컬럼이 없다
 * (연결은 갱신되지 않고 생성·삭제만 된다). {@link OauthProvider}와 같은 이유다.
 */
@Entity
@Table(name = "user_oauth_accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserOauthAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "oauth_account_seq")
    private Integer oauthAccountSeq;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_seq", nullable = false, updatable = false)
    private UserAccount account;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_seq", nullable = false, updatable = false)
    private OauthProvider provider;

    @Column(name = "oauth_subject", nullable = false, length = 255, updatable = false)
    private String oauthSubject;

    @Column(name = "reg_dttm", nullable = false, updatable = false)
    private Instant regDttm;

    private UserOauthAccount(UserAccount account, OauthProvider provider, String oauthSubject) {
        this.account = account;
        this.provider = provider;
        this.oauthSubject = oauthSubject;
    }

    public static UserOauthAccount link(UserAccount account, OauthProvider provider, String oauthSubject) {
        return new UserOauthAccount(account, provider, oauthSubject);
    }

    @PrePersist
    protected void onPrePersist() {
        this.regDttm = Instant.now();
    }
}
