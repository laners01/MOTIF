package com.ssafy.motif.domain.core.comment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

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

    /**
     * 답글을 펼칠 <b>루트</b> 댓글이 매달린 공지 (§9.2).
     *
     * <p>무엇이 "행 없음" 으로 떨어지는지는
     * {@link ChallengeCommentRepository#findRootTargetSeq} 에 적었다.
     */
    @Query("""
            select c.noticeSeq
            from NoticeComment c
              join User u on u.userSeq = c.userSeq
            where c.commentSeq = :commentSeq
              and c.parentCommentSeq is null
              and c.deleted = false
              and u.deleted = false
            """)
    Optional<Integer> findRootTargetSeq(@Param("commentSeq") Long commentSeq);

    /** 답글 한 페이지 (§9.2) — 등록순 고정. {@link ChallengeCommentRepository#findReplyPage} 참고. */
    @Query(value = """
            select new com.ssafy.motif.domain.core.comment.CommentRow(
                       c.commentSeq, c.content,
                       u.userSeq, u.nickname, u.profileImageKey,
                       c.likeCount, c.regDttm)
            from NoticeComment c
              join User u on u.userSeq = c.userSeq
            where c.parentCommentSeq = :parentSeq
              and c.deleted = false
              and u.deleted = false
            order by c.regDttm asc, c.commentSeq asc
            """,
            countQuery = """
            select count(c)
            from NoticeComment c
              join User u on u.userSeq = c.userSeq
            where c.parentCommentSeq = :parentSeq
              and c.deleted = false
              and u.deleted = false
            """)
    Page<CommentRow> findReplyPage(@Param("parentSeq") Long parentSeq, Pageable pageable);

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

    /**
     * <b>보이는</b> 댓글이 매달린 공지 (§9.5) — 루트·답글 가리지 않는다.
     *
     * <p>무엇이 "행 없음" 으로 떨어지는지는
     * {@link ChallengeCommentRepository#findVisibleTargetSeq} 에 적었다.
     */
    @Query("""
            select c.noticeSeq
            from NoticeComment c
              join User u on u.userSeq = c.userSeq
              left join NoticeComment p on p.commentSeq = c.parentCommentSeq
              left join User pu on pu.userSeq = p.userSeq
            where c.commentSeq = :commentSeq
              and c.deleted = false
              and u.deleted = false
              and (c.parentCommentSeq is null or (p.deleted = false and pu.deleted = false))
            """)
    Optional<Integer> findVisibleTargetSeq(@Param("commentSeq") Long commentSeq);

    /** 좋아요 (§9.5) — {@link ChallengeCommentRepository#insertLikeIfAbsent} 참고. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            insert into motif_core.notice_comment_likes (notice_comment_seq, user_seq)
            values (:commentSeq, :userSeq)
            on conflict do nothing
            """, nativeQuery = true)
    int insertLikeIfAbsent(@Param("commentSeq") Long commentSeq, @Param("userSeq") Integer userSeq);

    /** 좋아요 해제 (§9.5) — {@link ChallengeCommentRepository#deleteLikeIfPresent} 참고. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            delete from motif_core.notice_comment_likes
             where notice_comment_seq = :commentSeq and user_seq = :userSeq
            """, nativeQuery = true)
    int deleteLikeIfPresent(@Param("commentSeq") Long commentSeq,
                            @Param("userSeq") Integer userSeq);

    /** 좋아요 수 증감 (§9.5) — {@link ChallengeCommentRepository#addLikeCount} 참고. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update NoticeComment c
               set c.likeCount = c.likeCount + :delta
             where c.commentSeq = :commentSeq and c.likeCount + :delta >= 0
            """)
    int addLikeCount(@Param("commentSeq") Long commentSeq, @Param("delta") int delta);

    /** 살아 있는 직계 답글 seq (§9.4) — {@link ChallengeCommentRepository#findAliveReplySeqs} 참고. */
    @Query("""
            select c.commentSeq
            from NoticeComment c
            where c.parentCommentSeq = :parentSeq and c.deleted = false
            """)
    List<Long> findAliveReplySeqs(@Param("parentSeq") Long parentSeq);

    /** 소프트 삭제 (§9.4) — {@link ChallengeCommentRepository#softDelete} 참고. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update NoticeComment c
               set c.deleted = true, c.modDttm = :now, c.modUsrSeq = :modUsrSeq
             where c.commentSeq in :commentSeqs and c.deleted = false
            """)
    int softDelete(@Param("commentSeqs") Collection<Long> commentSeqs,
                   @Param("modUsrSeq") Integer modUsrSeq,
                   @Param("now") Instant now);

    /** 좋아요 하드 삭제 (§9.4) — {@link ChallengeCommentRepository#deleteLikes} 참고. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            delete from motif_core.notice_comment_likes
             where notice_comment_seq in (:commentSeqs)
            """, nativeQuery = true)
    int deleteLikes(@Param("commentSeqs") Collection<Long> commentSeqs);
}
