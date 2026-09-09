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
}
