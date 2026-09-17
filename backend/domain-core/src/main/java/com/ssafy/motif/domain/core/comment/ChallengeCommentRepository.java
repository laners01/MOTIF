package com.ssafy.motif.domain.core.comment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ChallengeCommentRepository extends JpaRepository<ChallengeComment, Long> {

    /**
     * 챌린지의 루트 댓글 한 페이지 (API 명세 §9.1) — <b>세 그룹 정렬을 쿼리가 건다</b>.
     *
     * <h4>정렬을 SQL 에 두는 이유</h4>
     * 명세는 "전체를 ① 대상 아티스트 ② 나 ③ 일반 순으로 정렬한 뒤 {@code size} 만큼
     * 자른다" 고 말한다. <b>페이지 안에서 다시 정렬하는 것으로는 이 계약을 지킬 수
     * 없다</b> — 아티스트 댓글이 200번째 행에 있으면 1페이지를 읽어 정렬해 봐야 거기
     * 없다. 자르기 전에 전체가 정렬돼 있어야 한다.
     *
     * <h4>{@code commentSeq desc} 타이브레이크</h4>
     * {@code reg_dttm} 만으로 자르면 <b>같은 시각</b>의 댓글들이 페이지마다 순서를 바꿔
     * 나온다 — 1페이지에 나온 댓글이 2페이지에 또 나오고 다른 하나는 영영 안 나온다.
     * PK 는 유일하고 IDENTITY 라 단조 증가하므로 최신순과 방향도 같다.
     *
     * <h4>비노출 (§1.8)</h4>
     * 삭제된 댓글과 <b>삭제된 사용자가 쓴 댓글</b>을 여기서 떨어뜨린다. 서비스에서
     * 거르면 페이지 크기가 들쭉날쭉해지고 {@code totalElements} 도 어긋난다.
     *
     * @param artistUserSeq 이 챌린지 아티스트의 {@code users.user_seq} — <b>1순위 판정의
     *                      유일한 근거</b>다. {@code role=ARTIST} 를 보면 남의 아티스트
     *                      계정까지 올라온다(명세 "다른 ARTIST 계정은 일반 댓글"). 아티스트
     *                      행을 못 찾은 경우 호출부가 어느 사용자와도 안 맞는 값을 넘긴다
     * @param viewerSeq     요청자 — 2순위이자 {@code isMine} 의 근거
     */
    @Query(value = """
            select new com.ssafy.motif.domain.core.comment.CommentRow(
                       c.commentSeq, c.content,
                       u.userSeq, u.nickname, u.profileImageKey,
                       c.likeCount, c.regDttm)
            from ChallengeComment c
              join User u on u.userSeq = c.userSeq
            where c.challengeSeq = :targetSeq
              and c.parentCommentSeq is null
              and c.deleted = false
              and u.deleted = false
            order by case when u.userSeq = :artistUserSeq then 0
                          when u.userSeq = :viewerSeq then 1
                          else 2 end,
                     c.regDttm desc,
                     c.commentSeq desc
            """,
            countQuery = """
            select count(c)
            from ChallengeComment c
              join User u on u.userSeq = c.userSeq
            where c.challengeSeq = :targetSeq
              and c.parentCommentSeq is null
              and c.deleted = false
              and u.deleted = false
            """)
    Page<CommentRow> findRootPage(@Param("targetSeq") Integer targetSeq,
                                  @Param("artistUserSeq") Integer artistUserSeq,
                                  @Param("viewerSeq") Integer viewerSeq,
                                  Pageable pageable);

    /**
     * 페이지에 실린 루트 댓글들의 답글 수 (§9.1 {@code replyCount}) — 한 번에.
     *
     * <p>비노출 규칙이 여기에도 걸린다 — 삭제된 답글·삭제 사용자의 답글은 §9.2 목록에
     * 나오지 않으므로 세지 않는다. <b>펼쳤을 때 나오는 수와 같아야 한다.</b>
     */
    @Query("""
            select new com.ssafy.motif.domain.core.comment.CommentReplyCount(
                       c.parentCommentSeq, count(c))
            from ChallengeComment c
              join User u on u.userSeq = c.userSeq
            where c.parentCommentSeq in :commentSeqs
              and c.deleted = false
              and u.deleted = false
            group by c.parentCommentSeq
            """)
    List<CommentReplyCount> countRepliesByParents(
            @Param("commentSeqs") Collection<Long> commentSeqs);

    /**
     * 페이지에 실린 댓글 중 <b>내가 좋아요한 것</b>만 (§9.1 {@code isLiked}).
     *
     * <p><b>네이티브다.</b> {@code challenge_comment_likes} 는 엔티티가 없고 토글(§9.5)
     * 티켓이 소유할 테이블이라 미리 매핑하지 않는다({@code NoticeRepository
     * .findLikedNoticeSeqs} 와 같은 규칙).
     *
     * <p>누르지 않은 댓글은 결과에 행이 없다 — 호출부가 {@code Set} 으로 대조한다.
     */
    @Query(value = """
            select challenge_comment_seq from motif_core.challenge_comment_likes
             where user_seq = :userSeq and challenge_comment_seq in (:commentSeqs)
            """, nativeQuery = true)
    List<Long> findLikedCommentSeqs(@Param("userSeq") Integer userSeq,
                                    @Param("commentSeqs") Collection<Long> commentSeqs);
}
