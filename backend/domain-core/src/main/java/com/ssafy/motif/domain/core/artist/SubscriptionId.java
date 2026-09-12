package com.ssafy.motif.domain.core.artist;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * {@code subscriptions} 복합 PK (DB SPEC v0.15 §10).
 *
 * <p>팀 표준대로 {@code @EmbeddedId} 를 쓴다 — {@code @IdClass} 는 금지
 * ({@code backend/README.md} 팀 표준 요약).
 *
 * <p>{@code equals}/{@code hashCode} 는 필수다. 영속성 컨텍스트가 이 값으로 엔티티를
 * 식별하므로 없으면 같은 구독을 서로 다른 행으로 취급한다.
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubscriptionId implements Serializable {

    @Column(name = "user_seq", nullable = false)
    private Integer userSeq;

    @Column(name = "artist_seq", nullable = false)
    private Integer artistSeq;

    public SubscriptionId(Integer userSeq, Integer artistSeq) {
        this.userSeq = userSeq;
        this.artistSeq = artistSeq;
    }
}
