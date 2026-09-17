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
 * 공지 댓글 (DB SPEC v0.15 §22, changeSet 0006-03).
 *
 * <p>구조·제약은 {@link ChallengeComment} 와 같다.
 *
 * <p>공지에는 {@code comment_enabled} 가 없다 — 댓글 잠금은 참여 영상 전용이다(§9.1
 * 실패표의 {@code COMMENT_DISABLED} 가 "(참여 영상)" 으로 한정된 이유).
 */
@Entity
@Table(name = "notice_comments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NoticeComment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notice_comment_seq")
    private Long commentSeq;

    @Column(name = "notice_seq", nullable = false, updatable = false)
    private Integer noticeSeq;

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
