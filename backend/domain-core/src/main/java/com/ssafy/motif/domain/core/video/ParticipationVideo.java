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
     * <p>{@code visibility} 는 {@link #changeVisibility} 가 따로 맡는다 — 값을 넣는
     * 일은 같지만 의미가 달라서(공개 범위는 인가 정책이다) 호출부에서 구분되는 편이
     * 낫다.
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
     * 공개 범위 전환 (API 명세 §8.9) — <b>키를 건드리지 않는다</b>.
     *
     * <p>{@code visibility} 는 <b>앱 안에서 누가 볼 자격이 있는가</b>이고, 그 자격을
     * CDN 에 전달하는 수단은 서명이다 — 다른 층이다(ADR-0013). 산출물은 게시 여부와
     * 무관하게 {@code private/} 한 곳에 있으므로 옮길 자리가 없다.
     *
     * <p><b>{@code PUBLIC → PRIVATE} 은 이것만으로 닫힌다.</b> 재생 중에는 CDN 직통이라
     * core-api 를 지나지 않지만 서명 갱신은 반드시 지나고, 거기서
     * {@code MediaPlaylistQueryService} 가 이 값을 다시 본다. 이미 발급된 서명이 만료될
     * 때까지(기본 10분)가 최대 지연이다.
     *
     * <p>예전에는 키를 함께 받아 {@code PUBLIC → PRIVATE} 에서 정본으로 되돌렸다 —
     * 공개 사본 트리가 있던 시절의 잔재이고, {@code null} 을 "유지" 로 겹쳐 쓰는 바람에
     * "정본을 못 찾음" 과 구분되지 않았다.
     */
    public void changeVisibility(Visibility visibility) {
        this.visibility = visibility;
    }
}
