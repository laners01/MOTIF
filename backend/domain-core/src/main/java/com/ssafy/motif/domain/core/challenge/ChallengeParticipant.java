package com.ssafy.motif.domain.core.challenge;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 교체 가능한 인물 슬롯 (DB SPEC v0.15 §13, changeSet 0004-02).
 *
 * <p><b>{@code participantMask} 는 단일 비트다</b>({@code 1, 2, 4, 8}) — DB CHECK 가
 * {@code mask & (mask-1) = 0} 으로 강제한다. 서버가 요청 배열의 <b>화면 좌→우 순서</b>대로
 * {@code 1 << index} 를 부여한다(어드민 명세 §3.7.2). 어드민은 비트값을 직접 입력하지
 * 않는다 — 수기 입력을 없애 조합 오류를 구조적으로 막는다.
 *
 * <p>{@code 0} 은 "무교체(원본)" 예약값이라 슬롯에 쓰지 않는다.
 *
 * <p><b>{@link com.ssafy.motif.common.jpa.BaseTimeEntity} 를 상속하지 않는다</b> —
 * 테이블에 {@code reg_dttm}·{@code mod_dttm} 이 없다. 슬롯 구성은 수정되지 않고
 * 통째로 지웠다 다시 만들어진다(§3.7.5).
 */
@Entity
@Table(name = "challenge_participants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChallengeParticipant {

    @EmbeddedId
    private ChallengeParticipantId id;

    @Column(name = "participant_mask", nullable = false)
    private int participantMask;

    @Embedded
    private Bbox bbox;

    /** 원본 px 기준 좌상단 원점 사각형 — DB CHECK 가 {@code 우하단 > 좌상단 ≥ 0} 을 강제한다. */
    @Embeddable
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class Bbox {

        @Column(name = "bbox_left_top_x", nullable = false)
        private int leftTopX;

        @Column(name = "bbox_left_top_y", nullable = false)
        private int leftTopY;

        @Column(name = "bbox_right_bottom_x", nullable = false)
        private int rightBottomX;

        @Column(name = "bbox_right_bottom_y", nullable = false)
        private int rightBottomY;

        public Bbox(int leftTopX, int leftTopY, int rightBottomX, int rightBottomY) {
            this.leftTopX = leftTopX;
            this.leftTopY = leftTopY;
            this.rightBottomX = rightBottomX;
            this.rightBottomY = rightBottomY;
        }
    }

    private ChallengeParticipant(ChallengeParticipantId id, int participantMask, Bbox bbox) {
        this.id = id;
        this.participantMask = participantMask;
        this.bbox = bbox;
    }

    public static ChallengeParticipant of(Integer challengeSeq, Integer artistSeq,
                                          int participantMask, Bbox bbox) {
        return new ChallengeParticipant(
                new ChallengeParticipantId(challengeSeq, artistSeq), participantMask, bbox);
    }

    public Integer artistSeq() {
        return id.getArtistSeq();
    }
}
