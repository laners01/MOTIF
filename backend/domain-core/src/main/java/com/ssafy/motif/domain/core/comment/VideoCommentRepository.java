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

    /**
     * 영상별 <b>내 최신 댓글 한 건</b> (API 명세 §4.9) — 활동 카드의 {@code myComment}.
     *
     * <p>{@code distinct on} 은 Postgres 전용이다. 표준 SQL 로는 영상마다 최대값을
     * 구하는 상관 서브쿼리나 윈도 함수를 써야 하는데, 이 레포는 이미 Postgres 에 묶여
     * 있고({@code on conflict}·{@code gin} 인덱스) 계획이 한 줄 더 짧다.
     *
     * <p><b>{@code order by} 가 계약의 일부다.</b> {@code distinct on} 은 정렬된 첫 행을
     * 남기므로 앞에 {@code video_seq} 가 와야 하고, 그 뒤 {@code reg_dttm desc} 가 "최신
     * 1개" 를 정한다. {@code video_comment_seq} 보조 키가 없으면 같은 초에 단 댓글 중
     * 어느 것이 남을지가 호출마다 흔들린다.
     *
     * <p>답글도 내 댓글이다 — {@code parent_comment_seq} 로 거르지 않는다. 그 경우
     * 카드는 부모 요약을 함께 싣고 {@code replies} 는 빈 배열이다(깊이 2 규칙).
     */
    @Query(value = """
            select distinct on (c.video_seq)
                   c.video_seq          as "videoSeq",
                   c.video_comment_seq  as "commentSeq",
                   c.content            as "content",
                   c.like_count         as "likeCount",
                   c.parent_comment_seq as "parentCommentSeq",
                   c.reg_dttm           as "regDttm"
              from motif_core.video_comments c
             where c.user_seq = :userSeq
               and c.is_deleted = false
               and c.video_seq in (:videoSeqs)
             order by c.video_seq, c.reg_dttm desc, c.video_comment_seq desc
            """, nativeQuery = true)
    List<MyCommentRow> findMyLatestByVideos(@Param("userSeq") Integer userSeq,
                                            @Param("videoSeqs") Collection<Long> videoSeqs);

    /**
     * 부모 댓글 <b>여럿</b>에 달린 답글을 각각 최신 {@code limit} 건까지 (§4.9 프리뷰).
     *
     * <p>부모마다 따로 물으면 카드 수만큼 왕복이다. 윈도 함수로 한 번에 끊는 이유가
     * 그것이고, {@code limit} 을 바인딩으로 받는 것은 프리뷰 개수가 <b>클라 확인 대기</b>
     * 인 기본안이라서다(§16) — 값이 바뀔 때 쿼리를 고치지 않는다.
     *
     * <p><b>고르는 순서와 싣는 순서가 다르다.</b> 안쪽 {@code row_number} 는 최신순으로
     * 2건을 고르고, 바깥 {@code order by} 는 그것을 <b>등록순</b>으로 되돌린다 — 화면은
     * 대화 순서로 읽히는 것이 맞고 명세의 예시도 그렇게 적혀 있다(§4.9).
     *
     * <p>삭제된 답글·삭제 사용자의 답글은 제외한다(§1.8) — {@code countRepliesByParents}
     * 가 세는 것과 같은 집합이라, 프리뷰 2건과 {@code replyCount} 가 어긋나지 않는다.
     */
    @Query(value = """
            select t."parentSeq"              as "parentSeq",
                   t."commentSeq"             as "commentSeq",
                   t."content"                as "content",
                   t."authorUserSeq"          as "authorUserSeq",
                   t."authorNickname"         as "authorNickname",
                   t."authorProfileImageKey"  as "authorProfileImageKey",
                   t."regDttm"                as "regDttm"
              from (select c.parent_comment_seq as "parentSeq",
                           c.video_comment_seq  as "commentSeq",
                           c.content            as "content",
                           u.user_seq           as "authorUserSeq",
                           u.nickname           as "authorNickname",
                           u.profile_image_key  as "authorProfileImageKey",
                           c.reg_dttm           as "regDttm",
                           row_number() over (partition by c.parent_comment_seq
                                              order by c.reg_dttm desc,
                                                       c.video_comment_seq desc) as rn
                      from motif_core.video_comments c
                      join motif_core.users u on u.user_seq = c.user_seq
                                             and u.is_deleted = false
                     where c.parent_comment_seq in (:parentSeqs)
                       and c.is_deleted = false) t
             where t.rn <= :limit
             order by t."parentSeq", t."regDttm" asc, t."commentSeq" asc
            """, nativeQuery = true)
    List<ReplyPreviewRow> findReplyPreviews(@Param("parentSeqs") Collection<Long> parentSeqs,
                                            @Param("limit") int limit);

    /**
     * 부모 댓글 요약 배치 (§4.9 {@code myComment.parentComment}) — 내 댓글이 답글일 때만.
     *
     * <p><b>보이지 않는 부모는 행이 없다.</b> 부모가 삭제됐거나 작성자가 탈퇴했으면
     * 호출부가 {@code parentComment} 를 {@code null} 로 둔다 — 카드는 남는다. 내가 단
     * 답글은 그대로 있고, 사라진 것은 그 위 맥락뿐이다.
     */
    @Query(value = """
            select c.video_comment_seq as "commentSeq",
                   c.content           as "content",
                   u.user_seq          as "authorUserSeq",
                   u.nickname          as "authorNickname",
                   u.profile_image_key as "authorProfileImageKey"
              from motif_core.video_comments c
              join motif_core.users u on u.user_seq = c.user_seq and u.is_deleted = false
             where c.video_comment_seq in (:commentSeqs)
               and c.is_deleted = false
            """, nativeQuery = true)
    List<ParentCommentRow> findParentSummaries(@Param("commentSeqs") Collection<Long> commentSeqs);

    /**
     * §4.9 카드의 내 댓글 — {@link #findMyLatestByVideos} 투영.
     *
     * <p>세 투영 모두 인터페이스인 것은 네이티브 쿼리라서다({@code MotifAssetRepository}
     * 의 중첩 투영과 같은 방식). 저장소 바깥에서 쓰이지 않아 안에 둔다.
     */
    interface MyCommentRow {

        Long getVideoSeq();

        Long getCommentSeq();

        String getContent();

        Integer getLikeCount();

        /** {@code null} 이면 루트 댓글 — 그때만 답글 프리뷰가 붙는다. */
        Long getParentCommentSeq();

        Instant getRegDttm();
    }

    /** §4.9 카드의 답글 프리뷰 한 줄 — {@link #findReplyPreviews} 투영. */
    interface ReplyPreviewRow {

        Long getParentSeq();

        Long getCommentSeq();

        String getContent();

        Integer getAuthorUserSeq();

        String getAuthorNickname();

        /** 프로필은 <b>키</b>다 — URL 로 바꾸는 것은 등급 판정이 있는 core-api 쪽이다. */
        String getAuthorProfileImageKey();

        Instant getRegDttm();
    }

    /** §4.9 카드의 부모 댓글 요약 — {@link #findParentSummaries} 투영. */
    interface ParentCommentRow {

        Long getCommentSeq();

        String getContent();

        Integer getAuthorUserSeq();

        String getAuthorNickname();

        String getAuthorProfileImageKey();
    }
}
