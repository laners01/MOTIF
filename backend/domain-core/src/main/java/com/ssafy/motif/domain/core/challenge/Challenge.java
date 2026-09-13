package com.ssafy.motif.domain.core.challenge;

import com.ssafy.motif.common.jpa.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 아티스트 원본 챌린지 (DB SPEC v0.15 §12, changeSet 0004-01).
 *
 * <p><b>상태 컬럼이 없다.</b> 등록은 원본·슬롯·편집본을 한 트랜잭션에 all-or-nothing 으로
 * 넣으므로(어드민 명세 §3.7.2) "편집본이 덜 올라간 미공개 챌린지" 상태가 생기지 않는다.
 * 노출 차단은 {@code isDeleted} 소프트 삭제 하나뿐이다.
 *
 * <p><b>{@code videoKey}·{@code artistSeq} 는 {@code updatable = false} 다.</b> 원본을
 * 바꾸면 슬롯 bbox 와 편집본 전부가 무효가 되므로 교체를 금지하고 삭제 후 재등록으로
 * 처리한다(회의 확정 R1). 메인 아티스트도 DB 상 불변이다. 매핑으로 막아 두면
 * 수정 API 가 실수로 dirty checking 에 태워도 UPDATE 문에 실리지 않는다.
 *
 * <p>등록 직후 {@code videoKey} 는 원본 MP4, {@code thumbnailKey} 는 <b>아직 없는 파일</b>을
 * 가리킨다 — app-media 가 {@code job.media} 를 처리하면 키가 {@code master.m3u8} 로 바뀌고
 * 썸네일이 생긴다(§1.4). 그 사이에도 MP4 로 재생된다.
 */
@Entity
@Table(name = "challenges")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Challenge extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "challenge_seq")
    private Integer challengeSeq;

    @Column(name = "artist_seq", nullable = false, updatable = false)
    private Integer artistSeq;

    @Column(name = "song_seq", nullable = false)
    private Integer songSeq;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "video_key", nullable = false, updatable = false)
    private String videoKey;

    @Column(name = "thumbnail_key", nullable = false)
    private String thumbnailKey;

    @Column(name = "duration_ms", nullable = false, updatable = false)
    private int durationMs;

    @Column(name = "view_count", nullable = false, updatable = false)
    private long viewCount;

    @Column(name = "participant_count", nullable = false, updatable = false)
    private int participantCount;

    @Column(name = "like_count", nullable = false, updatable = false)
    private int likeCount;

    @Column(name = "comment_count", nullable = false, updatable = false)
    private int commentCount;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    private Challenge(Integer artistSeq, Integer songSeq, String description,
                      String videoKey, String thumbnailKey, int durationMs) {
        this.artistSeq = artistSeq;
        this.songSeq = songSeq;
        this.description = description;
        this.videoKey = videoKey;
        this.thumbnailKey = thumbnailKey;
        this.durationMs = durationMs;
    }

    /** 어드민 등록 (§3.7.2). 카운터는 DB 기본값과 같은 0 으로 시작한다. */
    public static Challenge register(Integer artistSeq, Integer songSeq, String description,
                                     String videoKey, String thumbnailKey, int durationMs) {
        return new Challenge(artistSeq, songSeq, description, videoKey, thumbnailKey, durationMs);
    }

    /**
     * 소프트 삭제 (§3.7.7) — 이 플래그 <b>하나만</b> 세운다.
     *
     * <p>참여 영상·댓글·좋아요 행은 연쇄 삭제하지 않는다(DB SPEC §12 서비스 규칙).
     * 삭제된 챌린지에 속한 참여 영상은 자체 {@code is_deleted=FALSE} 여도 일반 API 가
     * 걸러 내므로 비노출은 이미 보장된다 — 자식을 지우면 복구 여지와 카운터 정합
     * ({@code participant_count}) 이 함께 사라진다.
     */
    public void softDelete() {
        this.deleted = true;
    }

    /** 소개글·곡만 바꾼다 (§3.7.4). {@code null} 은 "그 필드는 그대로" 라는 뜻이다. */
    public void update(String description, Integer songSeq) {
        if (description != null) {
            this.description = description;
        }
        if (songSeq != null) {
            this.songSeq = songSeq;
        }
    }
}
