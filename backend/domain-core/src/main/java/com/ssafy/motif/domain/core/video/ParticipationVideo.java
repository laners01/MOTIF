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
 * <p><b>생성자가 없다.</b> 행을 만드는 것은 게시(§8.8)뿐이고 그 API 가 아직 없다.
 * 수정은 §8.9 가 {@link #edit}·{@link #changeVisibility} 로 한다.
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

    /**
     * 게시 정보 수정 (API 명세 §8.9) — <b>부분 수정</b>.
     *
     * <p>{@code null} 은 "보내지 않았다" 라서 건너뛴다. 세 필드 모두 {@code null} 을
     * 유효한 값으로 쓰지 않으므로({@code description} 은 NOT NULL 이고 <b>빈 문자열</b>이
     * 지움이다) {@code JsonNullable} 같은 장치 없이 구분할 수 있다.
     *
     * <p>{@code visibility} 는 {@link #changeVisibility} 가 따로 맡는다 — 등급이 바뀌면
     * 키도 함께 움직여야 해서 값 하나로 끝나지 않는다.
     */
    public void edit(String description, Boolean commentEnabled, Integer modUsrSeq) {
        if (description != null) {
            this.description = description;
        }
        if (commentEnabled != null) {
            this.commentEnabled = commentEnabled;
        }
        this.modUsrSeq = modUsrSeq;
    }

    /**
     * 공개 범위 전환 (API 명세 §8.9).
     *
     * <p><b>키를 함께 받는 이유.</b> 등급을 정하는 것은 {@code visibility} 가 아니라
     * <b>키 프리픽스</b>다({@code MediaKeys#isProtected}). 이 값만 바꾸고 키를 두면
     * {@code PUBLIC → PRIVATE} 로 내린 영상이 계속 무서명 CDN URL 로 나간다 — 비공개로
     * 바꿨는데 링크를 아는 사람은 그대로 받는 상태가 된다.
     *
     * <p><b>{@code null} 이면 키를 유지한다.</b> {@code PRIVATE → PUBLIC} 은 공개 사본이
     * 만들어지기 전까지 가리킬 자리가 없어서 그렇다 — 없는 자리를 가리키면 CDN 이 404 를
     * 준다. 그동안은 보호 경로로 나가고 본인만 재생된다. 이것이 {@code MediaKeys}
     * javadoc 이 "정상적으로 존재한다" 고 적은 상태다.
     */
    public void changeVisibility(Visibility visibility, String videoKey, String thumbnailKey) {
        this.visibility = visibility;
        if (videoKey != null) {
            this.videoKey = videoKey;
        }
        if (thumbnailKey != null) {
            this.thumbnailKey = thumbnailKey;
        }
    }
}
