package com.ssafy.motif.domain.core.auth;

import com.ssafy.motif.domain.core.user.User;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * 기기별 로그인 유지·rotation (DB SPEC v0.15 §6, changeSet 0002-05).
 *
 * <p><b>원문을 저장하지 않는다</b> — {@code tokenHash}는 발급 토큰의 SHA-256 hex
 * (CHAR(64), UNIQUE). rotation(명세 §2.2)은 후속 티켓이며 이 티켓은 발급만 한다.
 *
 * <p><b>BaseTimeEntity를 상속하지 않는다</b> — {@code mod_dttm} 컬럼이 없다.
 * 폐기·사용 시각은 전용 컬럼({@code revoked_dttm}, {@code last_used_dttm})이 갖는다.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "token_seq")
    private Long tokenSeq;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_seq", nullable = false, updatable = false)
    private User user;

    /**
     * DDL이 {@code CHAR(64)}다(VARCHAR 아님) — SHA-256 hex가 정확히 64자라 가변 길이가
     * 필요 없다는 명세 선택이다. {@code @JdbcTypeCode} 없이는
     * {@code ddl-auto: validate}가 bpchar/varchar 불일치로 기동을 막는다.
     */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_hash", nullable = false, length = 64, updatable = false)
    private String tokenHash;

    @Column(name = "expires_dttm", nullable = false)
    private Instant expiresDttm;

    @Column(name = "revoked_dttm")
    private Instant revokedDttm;

    @Column(name = "last_used_dttm")
    private Instant lastUsedDttm;

    @Column(name = "reg_dttm", nullable = false, updatable = false)
    private Instant regDttm;

    private RefreshToken(User user, String tokenHash, Instant expiresDttm) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresDttm = expiresDttm;
    }

    public static RefreshToken issue(User user, String tokenHash, Instant expiresDttm) {
        return new RefreshToken(user, tokenHash, expiresDttm);
    }

    public void revoke() {
        this.revokedDttm = Instant.now();
    }

    @PrePersist
    protected void onPrePersist() {
        this.regDttm = Instant.now();
    }
}
