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

    /** 로그아웃·계정 정지 등으로 폐기한다 (명세 §2.3, DB SPEC §6). */
    public void revoke() {
        this.revokedDttm = Instant.now();
    }

    /**
     * 재발급(rotation)에 사용 — 마지막 사용 시각을 남기고 <b>같은 시각에 폐기</b>한다
     * (명세 §2.2, DB SPEC §6 "사용 시 `last_used_dttm`, `revoked_dttm` 갱신 후 새 행 INSERT").
     *
     * <p>두 시각이 같은 값인 것은 의도다 — refresh token 은 1회용이라 "사용된 순간"이
     * 곧 "폐기된 순간"이다. 이후 같은 토큰이 다시 오면 {@link #isRevoked()}가 참이라
     * 재사용 감지로 걸린다.
     */
    public void useForRotation() {
        Instant now = Instant.now();
        this.lastUsedDttm = now;
        this.revokedDttm = now;
    }

    /** 폐기됨 — 이 토큰이 다시 들어오면 재사용(탈취 신호)이다 (명세 §2.2). */
    public boolean isRevoked() {
        return this.revokedDttm != null;
    }

    /**
     * 이 토큰이 해당 사용자의 것인지 (명세 §2.3 로그아웃).
     *
     * <p>남의 토큰을 폐기해 주면 임의 사용자를 로그아웃시킬 수 있으므로, 폐기 전에
     * 반드시 확인한다. 미저장 엔티티({@code userSeq == null})나 인자가 null 이면
     * 소유로 보지 않는다.
     */
    public boolean isOwnedBy(Integer userSeq) {
        return userSeq != null && userSeq.equals(this.user.getUserSeq());
    }

    /** 만료 판정. 경계값({@code expiresDttm == at})은 만료로 본다. */
    public boolean isExpired(Instant at) {
        return !this.expiresDttm.isAfter(at);
    }

    @PrePersist
    protected void onPrePersist() {
        this.regDttm = Instant.now();
    }
}
