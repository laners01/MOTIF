package com.ssafy.motif.domain.core.artist;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 무료 팔로우 (DB SPEC v0.15 §10, changeSet 0003-02).
 *
 * <p>구독 해제 시 <b>하드 삭제</b>하는 단순 관계 테이블이라 대리키를 두지 않고
 * 복합 PK {@code (user_seq, artist_seq)} 를 쓴다 — 같은 사용자가 같은 아티스트를
 * 두 번 구독할 수 없다는 규칙이 PK 자체로 보장된다.
 *
 * <p><b>{@link com.ssafy.motif.common.jpa.BaseTimeEntity} 를 상속하지 않는다</b> —
 * {@code mod_dttm} 컬럼이 없다. 구독은 수정되지 않고 생기거나 사라질 뿐이다.
 */
@Entity
@Table(name = "subscriptions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Subscription {

    @EmbeddedId
    private SubscriptionId id;

    @Column(name = "reg_dttm", nullable = false, updatable = false)
    private Instant regDttm;

    private Subscription(SubscriptionId id) {
        this.id = id;
    }

    public static Subscription of(Integer userSeq, Integer artistSeq) {
        return new Subscription(new SubscriptionId(userSeq, artistSeq));
    }

    @PrePersist
    protected void onPrePersist() {
        this.regDttm = Instant.now();
    }
}
