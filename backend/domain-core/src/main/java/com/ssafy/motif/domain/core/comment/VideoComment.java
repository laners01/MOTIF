package com.ssafy.motif.domain.core.comment;

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
 * 참여 영상 댓글 (DB SPEC v0.15 §21, changeSet 0006-02).
 *
 * <p>구조·제약은 {@link ChallengeComment} 와 같다 — PK 필드를 {@code commentSeq} 로
 * 맞춘 이유도 그쪽 javadoc 에 있다. <b>세 엔티티를 하나로 합치지 않는 것은 의도다</b>:
 * 테이블이 셋이라 {@code @MappedSuperclass} 로 묶어도 쿼리는 어차피 셋이고, 대신
 * 대상별로 갈리는 규칙(영상만 {@code comment_enabled}, 영상만
 * {@code has_artist_comment} 재계산)을 공통 클래스가 떠안게 된다.
 *
 * <p><b>이 대상만 댓글이 잠긴다</b> — {@code participation_videos.comment_enabled=FALSE}
 * 면 §9.1 목록 조회까지 403 이다(데이터 동결이라 행은 지우지 않는다). 그 판정은 이
 * 엔티티가 아니라 영상 쪽에 있다.
 */
@Entity
@Table(name = "video_comments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VideoComment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "video_comment_seq")
    private Long commentSeq;

    @Column(name = "video_seq", nullable = false, updatable = false)
    private Long videoSeq;

    @Column(name = "user_seq", nullable = false, updatable = false)
    private Integer userSeq;

    /** {@code null} 이면 루트 댓글이다. */
    @Column(name = "parent_comment_seq", updatable = false)
    private Long parentCommentSeq;

    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "like_count", nullable = false)
    private int likeCount;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    @Column(name = "mod_usr_seq")
    private Integer modUsrSeq;
}
