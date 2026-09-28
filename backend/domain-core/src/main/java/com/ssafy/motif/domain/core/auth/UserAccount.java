package com.ssafy.motif.domain.core.auth;

import com.ssafy.motif.common.jpa.BaseTimeEntity;
import com.ssafy.motif.domain.core.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 인증 계정 상태. user당 1개 (DB SPEC v0.15 §2, changeSet 0002-02). */
@Entity
@Table(name = "user_accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAccount extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "account_seq")
    private Integer accountSeq;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_seq", nullable = false, updatable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 12)
    private AccountStatus accountStatus;

    private UserAccount(User user) {
        this.user = user;
        this.accountStatus = AccountStatus.ACTIVE;
    }

    public static UserAccount activeFor(User user) {
        return new UserAccount(user);
    }

    public boolean isSuspended() {
        return this.accountStatus == AccountStatus.SUSPENDED;
    }

    /**
     * 정지 (어드민 명세 §3.14.3).
     *
     * <p><b>이미 정지된 계정에는 부르지 않는다.</b> 호출부가 {@link #isSuspended()} 로
     * 먼저 걸러 409 {@code USER_ALREADY_SUSPENDED} 를 준다 — 여기서 조용히 통과시키면
     * 두 번째 정지가 refresh 토큰을 또 훑고 이벤트를 또 발행한다.
     *
     * <p>{@code mod_dttm} 은 {@code BaseTimeEntity} 의 {@code @PreUpdate} 가 채운다.
     */
    public void suspend() {
        this.accountStatus = AccountStatus.SUSPENDED;
    }

    /**
     * 정지 해제 (§3.14.4) — {@code ACTIVE} 전이만 한다.
     *
     * <p><b>토큰은 복구하지 않는다.</b> 폐기된 refresh 토큰은 되살릴 수 없고 되살려서도
     * 안 된다(재로그인으로 새로 받는다). 기기 토큰도 재로그인 시 재등록으로 살아난다 —
     * 그래서 해제 이벤트가 없다.
     */
    public void unsuspend() {
        this.accountStatus = AccountStatus.ACTIVE;
    }
}
