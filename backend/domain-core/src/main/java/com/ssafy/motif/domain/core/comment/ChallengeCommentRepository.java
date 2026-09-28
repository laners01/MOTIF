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
     * 답글을 펼칠 <b>루트</b> 댓글이 매달린 챌린지 (API 명세 §9.2).
     *
     * <p><b>"답글을 펼칠 수 있는가" 를 한 문장으로 답한다.</b> 미존재 · 삭제된 댓글 ·
     * <b>루트가 아님</b>(답글의 답글을 펼치려는 요청) · <b>탈퇴 사용자가 쓴 루트</b>
     * (§1.8 — 스레드 전체가 비노출이다)가 모두 "행 없음" 으로 떨어진다. 명세 §9.2 의
     * 404 는 이 넷이 같은 {@code COMMENT_NOT_FOUND} 다.
     *
     * <p>돌려주는 것이 대상 seq 인 이유: 댓글이 보이는지와 <b>대상이 보이는지</b>는 다른
     * 질문이고(잠긴 영상 · 삭제된 챌린지), 후자는 §9.1 과 같은 판정을 써야 한다.
     */
    @Query("""
            select c.challengeSeq
            from ChallengeComment c
              join User u on u.userSeq = c.userSeq
            where c.commentSeq = :commentSeq
              and c.parentCommentSeq is null
              and c.deleted = false
              and u.deleted = false
            """)
    Optional<Integer> findRootTargetSeq(@Param("commentSeq") Long commentSeq);

    /**
     * 루트 댓글의 답글 한 페이지 (API 명세 §9.2) — <b>등록순 고정</b>.
     *
     * <p>최신순이 아니다. 답글은 대화라서 쓴 순서대로 읽혀야 한다.
     *
     * <p>{@code commentSeq asc} 타이브레이크는 §9.1 과 같은 이유다 — 같은 시각의 답글이
     * 페이지 경계에서 중복되거나 빠지지 않게 한다.
     *
     * <p>정렬 그룹({@code artistUserSeq}·{@code viewerSeq})을 받지 않는다 — 세 그룹
     * 우선순위는 <b>루트 목록만</b>의 규칙이다(§9.1). 답글은 등록순 하나뿐이다.
     */
    @Query(value = """
            select new com.ssafy.motif.domain.core.comment.CommentRow(
                       c.commentSeq, c.content,
                       u.userSeq, u.nickname, u.profileImageKey,
                       c.likeCount, c.regDttm)
            from ChallengeComment c
              join User u on u.userSeq = c.userSeq
            where c.parentCommentSeq = :parentSeq
              and c.deleted = false
              and u.deleted = false
            order by c.regDttm asc, c.commentSeq asc
            """,
            countQuery = """
            select count(c)
            from ChallengeComment c
              join User u on u.userSeq = c.userSeq
            where c.parentCommentSeq = :parentSeq
              and c.deleted = false
              and u.deleted = false
            """)
    Page<CommentRow> findReplyPage(@Param("parentSeq") Long parentSeq, Pageable pageable);

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

    /**
     * 루트 댓글의 <b>아직 살아 있는</b> 직계 답글 seq (API 명세 §9.4).
     *
     * <p>이미 삭제된 답글을 함께 집으면 {@code deletedCount} 가 부풀고, 그만큼
     * {@code comment_count} 를 더 깎아 카운터가 음수 쪽으로 흐른다.
     *
     * <p><b>손자는 없다</b> — 깊이가 2단이라 직계가 전부다.
     */
    @Query("""
            select c.commentSeq
            from ChallengeComment c
            where c.parentCommentSeq = :parentSeq and c.deleted = false
            """)
    List<Long> findAliveReplySeqs(@Param("parentSeq") Long parentSeq);

    /**
     * <b>보이는</b> 댓글이 매달린 챌린지 (API 명세 §9.5) — 루트·답글 가리지 않는다.
     *
     * <p>{@link #findRootTargetSeq} 와 달리 루트를 요구하지 않는다. 좋아요는 답글에도
     * 달리기 때문이다. 대신 <b>답글이면 부모까지 보이는지</b> 확인한다 — 탈퇴 사용자가
     * 쓴 루트는 스레드 전체가 감춰지므로(§1.8) 그 아래 답글도 화면에 없다. 없는 것에
     * 좋아요가 눌리면 {@code like_count} 만 조용히 오른다.
     *
     * <p>미존재 · 삭제된 댓글 · 탈퇴 사용자의 댓글 · 감춰진 스레드의 답글이 모두 "행
     * 없음" 으로 떨어진다 — §9.5 의 404 는 이 넷이 같은 {@code COMMENT_NOT_FOUND} 다.
     */
    @Query("""
            select c.challengeSeq
            from ChallengeComment c
              join User u on u.userSeq = c.userSeq
              left join ChallengeComment p on p.commentSeq = c.parentCommentSeq
              left join User pu on pu.userSeq = p.userSeq
            where c.commentSeq = :commentSeq
              and c.deleted = false
              and u.deleted = false
              and (c.parentCommentSeq is null or (p.deleted = false and pu.deleted = false))
            """)
    Optional<Integer> findVisibleTargetSeq(@Param("commentSeq") Long commentSeq);

    /**
     * 좋아요 (API 명세 §9.5) — <b>이미 눌렀으면 아무것도 하지 않는다</b>.
     *
     * <p>멱등을 애플리케이션 분기로 만들지 않는다. "이미 눌렀나" 를 묻고 갈라지면 묻는
     * 시점과 쓰는 시점이 벌어져, 동시에 들어온 두 요청이 {@code like_count} 를 두 번
     * 움직인다. 복합 PK 가 판정자이고, <b>실제로 삽입된 행 수</b>만 호출부에 돌려준다
     * ({@code SubscriptionRepository.insertIfAbsent} 와 같은 방식).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            insert into motif_core.challenge_comment_likes (challenge_comment_seq, user_seq)
            values (:commentSeq, :userSeq)
            on conflict do nothing
            """, nativeQuery = true)
    int insertLikeIfAbsent(@Param("commentSeq") Long commentSeq, @Param("userSeq") Integer userSeq);

    /**
     * 좋아요 해제 (§9.5) — <b>하드 삭제</b>이고, 누르지 않았으면 아무것도 하지 않는다.
     *
     * <p>지운 행 수를 돌려주는 것이 요점이다. 그것을 보지 않고 카운터를 내리면 해제를
     * 두 번 받았을 때 {@code ck_challenge_comments_like_count CHECK (like_count >= 0)}
     * 을 위반해 DB 가 예외를 던진다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            delete from motif_core.challenge_comment_likes
             where challenge_comment_seq = :commentSeq and user_seq = :userSeq
            """, nativeQuery = true)
    int deleteLikeIfPresent(@Param("commentSeq") Long commentSeq,
                            @Param("userSeq") Integer userSeq);

    /**
     * 좋아요 수 증감 (§9.5) — 거울 카운터.
     *
     * <p>UPDATE 한 문장이다. 엔티티를 읽어 ±1 하고 저장하면 동시 요청에 유실된다.
     * 바닥 조건이 음수를 막는다({@code ChallengeRepository.addCommentCount} 와 같은 장치).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update ChallengeComment c
               set c.likeCount = c.likeCount + :delta
             where c.commentSeq = :commentSeq and c.likeCount + :delta >= 0
            """)
    int addLikeCount(@Param("commentSeq") Long commentSeq, @Param("delta") int delta);

    /**
     * 소프트 삭제 — <b>실제로 바뀐 행 수</b>를 돌려준다 (§9.4 {@code deletedCount}).
     *
     * <p>루트와 직계 답글을 한 문장에 지운다. 엔티티를 하나씩 들어 고치면 몇 건이
     * 실제로 바뀌었는지를 다시 세야 하고, 그 수가 곧 응답값이자
     * {@code comment_count} 감소폭이라 세는 곳이 둘이 되면 어긋난다.
     *
     * <p>{@code deleted = false} 조건이 <b>멱등을 막는다</b> — 이미 지워진 행은 세지
     * 않는다(§9.4 는 이미 삭제된 댓글에 404 를 준다).
     *
     * <p>{@code modDttm} 을 명시적으로 올린다 — 벌크 UPDATE 는
     * {@code BaseTimeEntity} 의 {@code @PreUpdate} 를 거치지 않는다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update ChallengeComment c
               set c.deleted = true, c.modDttm = :now, c.modUsrSeq = :modUsrSeq
             where c.commentSeq in :commentSeqs and c.deleted = false
            """)
    int softDelete(@Param("commentSeqs") Collection<Long> commentSeqs,
                   @Param("modUsrSeq") Integer modUsrSeq,
                   @Param("now") Instant now);

    /**
     * 삭제되는 댓글들의 좋아요 <b>하드 삭제</b> (§9.4).
     *
     * <p>소프트 삭제된 댓글의 좋아요를 남겨 두면 like_count 집계와 "내가 누른 목록" 에
     * 유령이 남는다. 되살릴 일이 없는 행이라 지운다.
     *
     * <p>네이티브인 이유는 {@code findLikedCommentSeqs} 와 같다 — 좋아요 테이블은
     * 엔티티가 없고 토글(§9.5) 티켓이 소유한다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            delete from motif_core.challenge_comment_likes
             where challenge_comment_seq in (:commentSeqs)
            """, nativeQuery = true)
    int deleteLikes(@Param("commentSeqs") Collection<Long> commentSeqs);

    /**
     * 챌린지별 <b>내 최신 댓글 한 건</b> (API 명세 §4.9) — 활동 카드의 {@code myComment}.
     *
     * <p>{@code distinct on}·{@code order by} 가 계약의 일부인 이유는
     * {@link VideoCommentRepository#findMyLatestByVideos} 에 적었다. 대상 칼럼
     * ({@code challenge_seq})과 PK 이름만 다르다.
     */
    @Query(value = """
            select distinct on (c.challenge_seq)
                   c.challenge_seq        as "challengeSeq",
                   c.challenge_comment_seq as "commentSeq",
                   c.content              as "content",
                   c.like_count           as "likeCount",
                   c.parent_comment_seq   as "parentCommentSeq",
                   c.reg_dttm             as "regDttm"
              from motif_core.challenge_comments c
             where c.user_seq = :userSeq
               and c.is_deleted = false
               and c.challenge_seq in (:challengeSeqs)
             order by c.challenge_seq, c.reg_dttm desc, c.challenge_comment_seq desc
            """, nativeQuery = true)
    List<MyCommentRow> findMyLatestByChallenges(
            @Param("userSeq") Integer userSeq,
            @Param("challengeSeqs") Collection<Integer> challengeSeqs);

    /**
     * 부모 댓글 <b>여럿</b>에 달린 답글을 각각 최신 {@code limit} 건까지 (§4.9 프리뷰).
     *
     * <p>왜 윈도 함수 한 번인지, 왜 고르는 순서와 싣는 순서가 다른지는
     * {@link VideoCommentRepository#findReplyPreviews} 에 적었다.
     */
    @Query(value = """
            select t."parentSeq"              as "parentSeq",
                   t."commentSeq"             as "commentSeq",
                   t."content"                as "content",
                   t."authorUserSeq"          as "authorUserSeq",
                   t."authorNickname"         as "authorNickname",
                   t."authorProfileImageKey"  as "authorProfileImageKey",
                   t."regDttm"                as "regDttm"
              from (select c.parent_comment_seq    as "parentSeq",
                           c.challenge_comment_seq as "commentSeq",
                           c.content               as "content",
                           u.user_seq              as "authorUserSeq",
                           u.nickname              as "authorNickname",
                           u.profile_image_key     as "authorProfileImageKey",
                           c.reg_dttm              as "regDttm",
                           row_number() over (partition by c.parent_comment_seq
                                              order by c.reg_dttm desc,
                                                       c.challenge_comment_seq desc) as rn
                      from motif_core.challenge_comments c
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
     * <p><b>보이지 않는 부모는 행이 없다</b> — 규칙은
     * {@link VideoCommentRepository#findParentSummaries} 와 같다.
     */
    @Query(value = """
            select c.challenge_comment_seq as "commentSeq",
                   c.content               as "content",
                   u.user_seq              as "authorUserSeq",
                   u.nickname              as "authorNickname",
                   u.profile_image_key     as "authorProfileImageKey"
              from motif_core.challenge_comments c
              join motif_core.users u on u.user_seq = c.user_seq and u.is_deleted = false
             where c.challenge_comment_seq in (:commentSeqs)
               and c.is_deleted = false
            """, nativeQuery = true)
    List<ParentCommentRow> findParentSummaries(@Param("commentSeqs") Collection<Long> commentSeqs);

    /**
     * §4.9 카드의 내 댓글 — {@link #findMyLatestByChallenges} 투영.
     *
     * <p><b>{@code VideoCommentRepository.MyCommentRow} 와 합치지 않는다.</b> 게터가
     * 하나 다르고({@code getChallengeSeq} ↔ {@code getVideoSeq}) 가리키는 seq 공간도
     * 다르다 — 합치면 호출부가 어느 대상의 seq 인지 모른 채 같은 칸을 읽게 된다.
     */
    interface MyCommentRow {

        Integer getChallengeSeq();

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
