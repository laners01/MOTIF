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
 * <p><b>생성자가 없다 — 읽기 전용이다.</b> 행을 만드는 것은 §9.3 작성이고 그 API 가 아직
 * 없다. 삭제(§9.4)·좋아요(§9.5)도 각자의 티켓이 여기에 행위를 더한다.
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
}
