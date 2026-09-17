package com.ssafy.motif.domain.core.comment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface NoticeCommentRepository extends JpaRepository<NoticeComment, Long> {

    /**
     * 공지의 루트 댓글 한 페이지 (API 명세 §9.1).
     *
     * <p>정렬·타이브레이크·비노출의 근거는 {@link ChallengeCommentRepository#findRootPage}
     * 에 적었다. 대상 칼럼({@code notice_seq})만 다르다.
     */
    @Query(value = """
            select new com.ssafy.motif.domain.core.comment.CommentRow(
                       c.commentSeq, c.content,
                       u.userSeq, u.nickname, u.profileImageKey,
                       c.likeCount, c.regDttm)
            from NoticeComment c
              join User u on u.userSeq = c.userSeq
            where c.noticeSeq = :targetSeq
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
            from NoticeComment c
              join User u on u.userSeq = c.userSeq
            where c.noticeSeq = :targetSeq
              and c.parentCommentSeq is null
              and c.deleted = false
              and u.deleted = false
            """)
    Page<CommentRow> findRootPage(@Param("targetSeq") Integer targetSeq,
                                  @Param("artistUserSeq") Integer artistUserSeq,
                                  @Param("viewerSeq") Integer viewerSeq,
                                  Pageable pageable);

    /** 페이지의 답글 수 — {@link ChallengeCommentRepository#countRepliesByParents} 와 같은 규칙. */
    @Query("""
            select new com.ssafy.motif.domain.core.comment.CommentReplyCount(
                       c.parentCommentSeq, count(c))
            from NoticeComment c
              join User u on u.userSeq = c.userSeq
            where c.parentCommentSeq in :commentSeqs
              and c.deleted = false
              and u.deleted = false
            group by c.parentCommentSeq
            """)
    List<CommentReplyCount> countRepliesByParents(
            @Param("commentSeqs") Collection<Long> commentSeqs);

    /** 내가 좋아요한 것만 — {@link ChallengeCommentRepository#findLikedCommentSeqs} 참고. */
    @Query(value = """
            select notice_comment_seq from motif_core.notice_comment_likes
             where user_seq = :userSeq and notice_comment_seq in (:commentSeqs)
            """, nativeQuery = true)
    List<Long> findLikedCommentSeqs(@Param("userSeq") Integer userSeq,
                                    @Param("commentSeqs") Collection<Long> commentSeqs);
}
