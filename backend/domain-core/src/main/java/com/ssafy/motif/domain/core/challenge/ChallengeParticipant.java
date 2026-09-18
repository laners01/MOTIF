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
 * 교체 가능한 인물 슬롯 (DB SPEC v0.15 §13, changeSet 0004-02 · 0017-01).
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
 * 통째로 지웠다 다시 만들어진다(§3.7.5). 앵커만 예외로 같은 행을 고친다(§3.7.9).
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

    /**
     * 촬영 전 선택 화면에서 이 슬롯의 프로필 원이 앉는 자리 — 썸네일 대비 0~1 비율.
     * <b>{@code null} 은 0017 이전 행</b>이다. 신규 등록은 서비스가 필수로 받고,
     * 옛 행은 어드민 상세에서 {@link #updateAnchor} 로 채운다.
     */
    @Embedded
    private Anchor anchor;

    /**
     * 프로필 원 중심 {@code (x, y)} 와 반지름 {@code r}, 전부 비율이다 — {@code x·r} 은 썸네일
     * 가로, {@code y} 는 세로 대비. 시안(402×874, 지름 80)의 {@code r = 40/402 ≈ 0.0995} 가
     * 어드민 기본값이다. 클라이언트는 렌더 크기를 곱해 px 로 만든다. DB CHECK 가
     * {@code 0 ≤ x,y ≤ 1 · 0 < r ≤ 0.5} 를 강제한다.
     */
    @Embeddable
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class Anchor {

        @Column(name = "anchor_x")
        private Double x;

        @Column(name = "anchor_y")
        private Double y;

        @Column(name = "anchor_r")
        private Double r;

        public Anchor(double x, double y, double r) {
            this.x = x;
            this.y = y;
            this.r = r;
        }

        /** 세 컬럼이 전부 NULL 인 옛 행은 Hibernate 가 임베디드를 {@code null} 로 주지만, 부분 NULL 도 같은 뜻으로 본다. */
        public boolean isSet() {
            return x != null && y != null && r != null;
        }
    }

    private ChallengeParticipant(ChallengeParticipantId id, int participantMask, Anchor anchor) {
        this.id = id;
        this.participantMask = participantMask;
        this.anchor = anchor;
    }

    public static ChallengeParticipant of(Integer challengeSeq, Integer artistSeq,
                                          int participantMask, Anchor anchor) {
        return new ChallengeParticipant(
                new ChallengeParticipantId(challengeSeq, artistSeq), participantMask, anchor);
    }

    /** 앵커만 고친다(§3.7.9) — 슬롯 구성·마스크는 그대로라 편집본 완결성에 영향이 없다. */
    public void updateAnchor(Anchor anchor) {
        this.anchor = anchor;
    }

    public Integer artistSeq() {
        return id.getArtistSeq();
    }

    /** 앵커가 채워진 행만 값을 준다 — 옛 행은 {@code null}. */
    public Anchor anchorOrNull() {
        return anchor != null && anchor.isSet() ? anchor : null;
    }
}
