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

public interface VideoCommentRepository extends JpaRepository<VideoComment, Long> {

    /**
     * 참여 영상의 루트 댓글 한 페이지 (API 명세 §9.1).
     *
     * <p>정렬·타이브레이크·비노출의 근거는 {@link ChallengeCommentRepository#findRootPage}
     * 에 적었다. 대상 칼럼({@code video_seq})과 대상 아티스트의 출처만 다르다 —
     * 참여 영상의 {@code artist_seq} 는 원본 챌린지에서 <b>복사된</b> 값이다.
     *
     * <p>{@code comment_enabled=FALSE} 는 여기서 보지 않는다 — 그건 목록을 <b>내려주지
     * 않는</b> 판정(403)이라 쿼리 이전에 끝나야 한다({@code CommentTargetGuard}).
     * 조건으로 넣으면 "댓글이 0건" 과 "댓글이 잠김" 이 같은 응답이 된다.
     */
    @Query(value = """
            select new com.ssafy.motif.domain.core.comment.CommentRow(
                       c.commentSeq, c.content,
                       u.userSeq, u.nickname, u.profileImageKey,
                       c.likeCount, c.regDttm)
            from VideoComment c
              join User u on u.userSeq = c.userSeq
            where c.videoSeq = :targetSeq
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
            from VideoComment c
              join User u on u.userSeq = c.userSeq
            where c.videoSeq = :targetSeq
              and c.parentCommentSeq is null
              and c.deleted = false
              and u.deleted = false
            """)
    Page<CommentRow> findRootPage(@Param("targetSeq") Long targetSeq,
                                  @Param("artistUserSeq") Integer artistUserSeq,
                                  @Param("viewerSeq") Integer viewerSeq,
                                  Pageable pageable);

    /**
     * 답글을 펼칠 <b>루트</b> 댓글이 매달린 참여 영상 (§9.2).
     *
     * <p>무엇이 "행 없음" 으로 떨어지는지는
     * {@link ChallengeCommentRepository#findRootTargetSeq} 에 적었다.
     */
    @Query("""
            select c.videoSeq
            from VideoComment c
              join User u on u.userSeq = c.userSeq
            where c.commentSeq = :commentSeq
              and c.parentCommentSeq is null
              and c.deleted = false
              and u.deleted = false
            """)
    Optional<Long> findRootTargetSeq(@Param("commentSeq") Long commentSeq);

    /** 답글 한 페이지 (§9.2) — 등록순 고정. {@link ChallengeCommentRepository#findReplyPage} 참고. */
    @Query(value = """
            select new com.ssafy.motif.domain.core.comment.CommentRow(
                       c.commentSeq, c.content,
                       u.userSeq, u.nickname, u.profileImageKey,
                       c.likeCount, c.regDttm)
            from VideoComment c
              join User u on u.userSeq = c.userSeq
            where c.parentCommentSeq = :parentSeq
              and c.deleted = false
              and u.deleted = false
            order by c.regDttm asc, c.commentSeq asc
            """,
            countQuery = """
            select count(c)
            from VideoComment c
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
            from VideoComment c
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
            select video_comment_seq from motif_core.video_comment_likes
             where user_seq = :userSeq and video_comment_seq in (:commentSeqs)
            """, nativeQuery = true)
    List<Long> findLikedCommentSeqs(@Param("userSeq") Integer userSeq,
                                    @Param("commentSeqs") Collection<Long> commentSeqs);

    /**
     * <b>보이는</b> 댓글이 매달린 참여 영상 (§9.5) — 루트·답글 가리지 않는다.
     *
     * <p>무엇이 "행 없음" 으로 떨어지는지는
     * {@link ChallengeCommentRepository#findVisibleTargetSeq} 에 적었다.
     */
    @Query("""
            select c.videoSeq
            from VideoComment c
              join User u on u.userSeq = c.userSeq
              left join VideoComment p on p.commentSeq = c.parentCommentSeq
              left join User pu on pu.userSeq = p.userSeq
            where c.commentSeq = :commentSeq
              and c.deleted = false
              and u.deleted = false
              and (c.parentCommentSeq is null or (p.deleted = false and pu.deleted = false))
            """)
    Optional<Long> findVisibleTargetSeq(@Param("commentSeq") Long commentSeq);

    /** 좋아요 (§9.5) — {@link ChallengeCommentRepository#insertLikeIfAbsent} 참고. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            insert into motif_core.video_comment_likes (video_comment_seq, user_seq)
            values (:commentSeq, :userSeq)
            on conflict do nothing
            """, nativeQuery = true)
    int insertLikeIfAbsent(@Param("commentSeq") Long commentSeq, @Param("userSeq") Integer userSeq);

    /** 좋아요 해제 (§9.5) — {@link ChallengeCommentRepository#deleteLikeIfPresent} 참고. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            delete from motif_core.video_comment_likes
             where video_comment_seq = :commentSeq and user_seq = :userSeq
            """, nativeQuery = true)
    int deleteLikeIfPresent(@Param("commentSeq") Long commentSeq,
                            @Param("userSeq") Integer userSeq);

    /** 좋아요 수 증감 (§9.5) — {@link ChallengeCommentRepository#addLikeCount} 참고. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update VideoComment c
               set c.likeCount = c.likeCount + :delta
             where c.commentSeq = :commentSeq and c.likeCount + :delta >= 0
            """)
    int addLikeCount(@Param("commentSeq") Long commentSeq, @Param("delta") int delta);

    /** 살아 있는 직계 답글 seq (§9.4) — {@link ChallengeCommentRepository#findAliveReplySeqs} 참고. */
    @Query("""
            select c.commentSeq
            from VideoComment c
            where c.parentCommentSeq = :parentSeq and c.deleted = false
            """)
    List<Long> findAliveReplySeqs(@Param("parentSeq") Long parentSeq);

    /** 소프트 삭제 (§9.4) — {@link ChallengeCommentRepository#softDelete} 참고. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update VideoComment c
               set c.deleted = true, c.modDttm = :now, c.modUsrSeq = :modUsrSeq
             where c.commentSeq in :commentSeqs and c.deleted = false
            """)
    int softDelete(@Param("commentSeqs") Collection<Long> commentSeqs,
                   @Param("modUsrSeq") Integer modUsrSeq,
                   @Param("now") Instant now);

    /** 좋아요 하드 삭제 (§9.4) — {@link ChallengeCommentRepository#deleteLikes} 참고. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            delete from motif_core.video_comment_likes
             where video_comment_seq in (:commentSeqs)
            """, nativeQuery = true)
    int deleteLikes(@Param("commentSeqs") Collection<Long> commentSeqs);

    /**
     * 이 영상에 <b>그 아티스트의 댓글이 남아 있는가</b>
     * ({@code participation_videos.has_artist_comment} 재계산 — §9.3·§9.4).
     *
     * <p>플래그를 지우는 쪽에서 특히 필요하다 — 아티스트 댓글을 하나 지웠다고 무조건
     * {@code FALSE} 로 내리면 <b>다른 아티스트 댓글이 남아 있는데도</b> 피드 정렬
     * 가산점이 사라진다(§8.1). 켤 때는 자명하지만 끌 때는 세어 봐야 한다.
     *
     * <p>답글도 아티스트 댓글이다 — 루트만 세지 않는다.
     */
    @Query("""
            select count(c) > 0
            from VideoComment c
            where c.videoSeq = :videoSeq
              and c.userSeq = :artistUserSeq
              and c.deleted = false
            """)
    boolean existsArtistComment(@Param("videoSeq") Long videoSeq,
                                @Param("artistUserSeq") Integer artistUserSeq);
}
