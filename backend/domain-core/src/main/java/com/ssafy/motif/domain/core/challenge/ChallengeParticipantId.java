package com.ssafy.motif.domain.core.challenge;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** {@code challenge_participants} 복합 PK (DB SPEC v0.15 §13). 팀 표준대로 {@code @EmbeddedId}. */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChallengeParticipantId implements Serializable {

    @Column(name = "challenge_seq", nullable = false)
    private Integer challengeSeq;

    @Column(name = "artist_seq", nullable = false)
    private Integer artistSeq;

    public ChallengeParticipantId(Integer challengeSeq, Integer artistSeq) {
        this.challengeSeq = challengeSeq;
        this.artistSeq = artistSeq;
    }
}
