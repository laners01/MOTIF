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
 * 챌린지 댓글 (DB SPEC v0.15 §20, changeSet 0006-01).
 *
 * <p><b>만드는 길은 {@link #write} 하나다</b>(§9.3). <b>지우는 길은 여기 없다</b> —
 * 삭제(§9.4)는 루트와 직계 답글을 함께 소프트 삭제해야 하고 그 수가 곧 응답의
 * {@code deletedCount} 라서, 엔티티를 하나씩 들어 고치는 대신 리포지토리의 벌크
 * UPDATE 가 한 문장으로 처리한다({@code softDelete}). 좋아요(§9.5)는 각자의 티켓이
 * 행위를 더한다.
 *
 * <p><b>PK 필드 이름이 칼럼명과 다르다</b> — 칼럼은 테이블마다
 * {@code challenge_comment_seq}·{@code video_comment_seq}·{@code notice_comment_seq} 로
 * 갈리지만 자바 필드는 셋 다 {@code commentSeq} 다. 명세 §9 가 세 대상을 <b>하나의
 * 계약</b>으로 못박았으므로({@code /comments/{type}/{commentSeq}}) 쿼리도 같은 모양이어야
 * 한다 — 이름이 갈리면 같은 문장을 세 번 다르게 쓰게 되고, 한쪽만 고쳤을 때 세 대상의
 * 댓글이 서로 다르게 동작한다.
 *
 * <p>깊이는 2단까지다 — {@code parentCommentSeq} 가 가리키는 것은 반드시 <b>루트</b>
 * 댓글이어야 한다. 그 교차 행 규칙은 DB CHECK 가 아니라 서비스가 지킨다(0006 주석).
 */
@Entity
@Table(name = "challenge_comments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChallengeComment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "challenge_comment_seq")
    private Long commentSeq;

    @Column(name = "challenge_seq", nullable = false, updatable = false)
    private Integer challengeSeq;

    @Column(name = "user_seq", nullable = false, updatable = false)
    private Integer userSeq;

    /** {@code null} 이면 루트 댓글이다 — §9.1 목록이 고르는 기준이 이것이다. */
    @Column(name = "parent_comment_seq", updatable = false)
    private Long parentCommentSeq;

    @Column(name = "content", nullable = false)
    private String content;

    /** 거울 카운터 — 좋아요 토글(§9.5)이 원자적 UPDATE 로 증감한다. */
    @Column(name = "like_count", nullable = false)
    private int likeCount;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    @Column(name = "mod_usr_seq")
    private Integer modUsrSeq;

    /**
     * 댓글·답글 작성 (API 명세 §9.3).
     *
     * <p>{@code parentCommentSeq} 가 {@code null} 이면 루트다. <b>그 값이 루트인지,
     * 같은 챌린지의 댓글인지는 여기서 묻지 않는다</b> — 다른 행을 읽어야 답할 수 있는
     * 교차 행 규칙이라 서비스가 확인한다(changeSet 0006 주석도 같은 이유로 CHECK 를
     * 걸지 않았다).
     *
     * <p>{@code likeCount}·{@code deleted} 는 DB 기본값(0·FALSE)과 같은 자바 기본값으로
     * 시작한다.
     */
    public static ChallengeComment write(Integer challengeSeq, Integer userSeq,
                                         Long parentCommentSeq, String content) {
        ChallengeComment comment = new ChallengeComment();
        comment.challengeSeq = challengeSeq;
        comment.userSeq = userSeq;
        comment.parentCommentSeq = parentCommentSeq;
        comment.content = content;
        return comment;
    }
}
