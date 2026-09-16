package com.ssafy.motif.domain.core.video;

import com.ssafy.motif.common.jpa.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 게시된 챌린지 참여 영상 (DB SPEC v0.15 §19, changeSet 0005-01).
 *
 * <p><b>행의 존재 자체가 "게시됐다"는 뜻이다.</b> 합성은 게시보다 먼저 끝나 있고
 * (게시 전 프리뷰도 재생 대상이다 — §1.7), 게시(§8.1)에서 비로소 이 행이 생긴다.
 * 그래서 {@code composite_jobs} 는 있는데 여기 행이 없는 구간이 정상적으로 존재하며,
 * 그 구간의 영상은 <b>소유자만</b> 볼 수 있다.
 *
 * <p><b>생성자가 없다.</b> 게시 API(§8.1)가 이 티켓 범위 밖이다. 여기서는 마이페이지
 * 프리뷰(§4.1)와 보호 등급 인가(§1.7)를 위해 읽기만 한다.
 *
 * <p>{@code jobSeq} 에 {@code uq_participation_videos_job} 이 걸려 있다 — 잡 하나에
 * 게시물 하나다. 보호 등급 인가가 {@code jobSeq} 로 이 행을 찾는 근거다.
 */
@Entity
@Table(name = "participation_videos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ParticipationVideo extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "video_seq")
    private Long videoSeq;

    @Column(name = "challenge_seq", nullable = false, updatable = false)
    private Integer challengeSeq;

    @Column(name = "artist_seq", nullable = false, updatable = false)
    private Integer artistSeq;

    @Column(name = "user_seq", nullable = false, updatable = false)
    private Integer userSeq;

    @Column(name = "job_seq", nullable = false, updatable = false)
    private Long jobSeq;

    @Column(name = "description", nullable = false)
    private String description;

    /** 마스터 재생목록 키 — {@code {프리픽스}/master.m3u8} (ADR-0010 D4). */
    @Column(name = "video_key", nullable = false, length = 255)
    private String videoKey;

    /** {@code {프리픽스}/thumb.jpg}. 등급은 이 키의 프리픽스가 정한다. */
    @Column(name = "thumbnail_key", nullable = false, length = 255)
    private String thumbnailKey;

    @Column(name = "duration_ms", nullable = false)
    private Integer durationMs;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 10)
    private Visibility visibility;

    @Column(name = "comment_enabled", nullable = false)
    private boolean commentEnabled;

    @Column(name = "view_count", nullable = false)
    private Long viewCount;

    @Column(name = "like_count", nullable = false)
    private Integer likeCount;

    @Column(name = "comment_count", nullable = false)
    private Integer commentCount;

    @Column(name = "has_artist_like", nullable = false)
    private boolean hasArtistLike;

    @Column(name = "has_artist_comment", nullable = false)
    private boolean hasArtistComment;

    @Column(name = "mod_usr_seq")
    private Integer modUsrSeq;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    public boolean isPublic() {
        return visibility == Visibility.PUBLIC;
    }
}
