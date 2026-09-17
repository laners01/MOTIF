package com.ssafy.motif.domain.core.notice;

import com.ssafy.motif.domain.core.artist.ArtistCount;
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

public interface NoticeRepository extends JpaRepository<Notice, Integer> {

    /**
     * 어드민 목록 (어드민 명세 §3.10.1) — 아티스트 필터 · 삭제분 포함 여부 · 최신순.
     *
     * <p><b>정렬은 서버가 고정한다</b>(§1.5 "서버 고정 정렬 엔드포인트는 sort 무시").
     * {@code reg_dttm} 이 아니라 PK 역순인 이유: IDENTITY 라 단조 증가라서 최신순과
     * 같은 결과인데, 같은 초에 등록된 공지들이 순서를 바꿔 가며 나오는 일이 없다 —
     * 페이지를 넘길 때 행이 중복되거나 건너뛰어지지 않는다.
     *
     * <p>파라미터 두 개가 {@code null}·{@code true} 면 조건이 통째로 빠진다. 조합마다
     * 파생 메서드를 만들면 네 개가 되고, 필터가 하나 늘 때마다 두 배가 된다.
     *
     * <p>count 쿼리를 명시한다 — 파생 count 는 {@code order by} 를 떼어 내는 과정에서
     * 조건이 있는 쿼리를 잘못 다룰 여지가 있고, 여기서 틀리면 {@code totalPages} 만
     * 조용히 어긋난다.
     */
    @Query(value = """
            select n from Notice n
            where (:artistSeq is null or n.artistSeq = :artistSeq)
              and (:includeDeleted = true or n.deleted = false)
            order by n.noticeSeq desc
            """,
            countQuery = """
            select count(n) from Notice n
            where (:artistSeq is null or n.artistSeq = :artistSeq)
              and (:includeDeleted = true or n.deleted = false)
            """)
    Page<Notice> findAdminPage(@Param("artistSeq") Integer artistSeq,
                               @Param("includeDeleted") boolean includeDeleted,
                               Pageable pageable);
    /** 아티스트 목록·상세의 {@code noticeCount} (어드민 §3.5.1) — 페이지 전체를 한 번에. */
    @Query("""
            select new com.ssafy.motif.domain.core.artist.ArtistCount(n.artistSeq, count(n))
            from Notice n
            where n.artistSeq in :artistSeqs and n.deleted = false
            group by n.artistSeq
            """)
    List<ArtistCount> countByArtists(@Param("artistSeqs") Collection<Integer> artistSeqs);

    /**
     * 댓글 수 증감 (API 명세 §9.3·§9.4) — 거울 카운터.
     *
     * <p>규칙은 {@code ChallengeRepository.addCommentCount} 와 같다: 작성 {@code +1},
     * 삭제는 실제로 지워진 행 수만큼 음수, 바닥은 0.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Notice n
               set n.commentCount = n.commentCount + :delta
             where n.noticeSeq = :noticeSeq and n.commentCount + :delta >= 0
            """)
    int addCommentCount(@Param("noticeSeq") Integer noticeSeq, @Param("delta") int delta);

    /** 삭제된 공지는 없는 것으로 본다 — 수정·삭제 대상 조회(§3.10.4·§3.10.5). */
    Optional<Notice> findByNoticeSeqAndDeletedFalse(Integer noticeSeq);

    /**
     * 팬 앱 공지 목록 <b>첫 페이지</b> (API 명세 §5.5) — 커서 없는 최초 호출.
     *
     * <p><b>커서가 있는 경우와 메서드를 나눈 것은 의도다.</b> {@code :cursor is null or
     * n.noticeSeq < :cursor} 로 한 방에 쓰면 최초 호출에서 타입 없는 {@code null} 이
     * 파라미터로 들어가고, Postgres 가 그것을 {@code bytea} 로 추론해 비교 연산자를 못
     * 찾는 500 이 난다 — {@code ArtistRepository.search} 의 {@code q} 가 2026-09-13
     * 운영에서 정확히 이렇게 죽었다({@code function lower(bytea) does not exist}).
     * 파생 메서드 두 개면 {@code null} 이 쿼리에 닿는 경로 자체가 없다.
     *
     * <p>정렬은 <b>PK 역순 고정</b>이고 이것이 곧 커서 키다(§5.5 "최신순 고정"). 커서
     * 목록에서 {@code reg_dttm} 을 키로 쓰면 같은 시각의 공지에서 행이 중복되거나
     * 건너뛰어진다 — PK 는 유일해서 그 일이 없다.
     *
     * <p>호출부는 {@code size + 1} 건을 요청해 넘치는지로 {@code hasNext} 를 본다 —
     * 총 개수를 세지 않는 것이 커서 페이지네이션의 요점이다(§1.9).
     */
    List<Notice> findByArtistSeqAndDeletedFalseOrderByNoticeSeqDesc(Integer artistSeq,
                                                                    Pageable pageable);

    /** 팬 앱 공지 목록 <b>다음 페이지</b> — {@code cursor} 는 직전 페이지 마지막 항목의 PK. */
    List<Notice> findByArtistSeqAndDeletedFalseAndNoticeSeqLessThanOrderByNoticeSeqDesc(
            Integer artistSeq, Integer cursor, Pageable pageable);

    /**
     * 공지 목록 한 페이지의 {@code isLiked} (API 명세 §5.5) — 내가 누른 것만 골라 온다.
     *
     * <p><b>네이티브다.</b> {@code notice_likes} 에는 엔티티가 없다 — 토글(§10.2)도 조회도
     * 필요한 것이 <b>행의 존재 여부와 영향 행 수</b>뿐이라 매핑할 상태가 없다
     * ({@code ChallengeRepository.countActiveJobs} 와 같은 규칙).
     *
     * <p>공지마다 한 번씩 물으면 한 페이지(최대 100건)에 100번이다 — 페이지의 seq 를
     * 통째로 넘겨 <b>내가 누른 것만</b> 받고 호출부가 {@code Set} 으로 대조한다.
     * 누르지 않은 공지는 결과에 행이 없다.
     */
    @Query(value = """
            select notice_seq from motif_core.notice_likes
             where user_seq = :userSeq and notice_seq in (:noticeSeqs)
            """, nativeQuery = true)
    List<Integer> findLikedNoticeSeqs(@Param("userSeq") Integer userSeq,
                                      @Param("noticeSeqs") Collection<Integer> noticeSeqs);

    /**
     * 좋아요 대상 존재 판정 (API 명세 §10.2) — 삭제된 공지는 없는 것으로 본다(§1.8).
     *
     * <p>{@link #findByNoticeSeqAndDeletedFalse} 가 아니라 {@code exists} 인 이유: §10.2 는
     * <b>204 라 돌려줄 값이 없다</b>. 본문·이미지가 달린 행을 영속성 컨텍스트에 실어 봐야
     * 쓰는 곳이 없고, 뒤따르는 증감이 벌크 UPDATE 라 그 행은 곧 버려진다.
     */
    boolean existsByNoticeSeqAndDeletedFalse(Integer noticeSeq);

    /**
     * 좋아요 (§10.2) — 이미 누른 상태면 <b>0을 돌려준다</b>.
     *
     * <p><b>멱등이 애플리케이션 분기가 아니라 복합 PK 에서 나온다.</b> "이미 눌렀나" 를
     * 먼저 묻고 갈라지면 묻는 시점과 쓰는 시점이 벌어져, 동시에 들어온 두 요청이
     * {@code like_count} 를 두 번 올린다. 호출부는 <b>실제로 삽입된 행 수</b>만 보고
     * 카운터를 움직인다({@code NoticeCommentRepository.insertLikeIfAbsent} 와 같은 방식).
     *
     * <p>네이티브인 이유는 {@code on conflict do nothing} 이 JPQL 에 없어서다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            insert into motif_core.notice_likes (notice_seq, user_seq)
            values (:noticeSeq, :userSeq)
            on conflict do nothing
            """, nativeQuery = true)
    int insertLikeIfAbsent(@Param("noticeSeq") Integer noticeSeq,
                           @Param("userSeq") Integer userSeq);

    /** 좋아요 해제 (§10.2) — <b>하드 삭제</b>다. 누르지 않은 상태면 0을 돌려준다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            delete from motif_core.notice_likes
             where notice_seq = :noticeSeq and user_seq = :userSeq
            """, nativeQuery = true)
    int deleteLikeIfPresent(@Param("noticeSeq") Integer noticeSeq,
                            @Param("userSeq") Integer userSeq);

    /**
     * 좋아요 수 증감 (§10.2) — 거울 카운터. {@link #addCommentCount} 와 같은 규칙이다.
     *
     * <p>{@code like_count + :delta >= 0} 가드가 있어 음수로 내려가지 않는다 —
     * {@code ck_notices_like_count} 위반으로 트랜잭션이 통째로 죽는 대신 0행 UPDATE 다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Notice n
               set n.likeCount = n.likeCount + :delta
             where n.noticeSeq = :noticeSeq and n.likeCount + :delta >= 0
            """)
    int addLikeCount(@Param("noticeSeq") Integer noticeSeq, @Param("delta") int delta);

    /**
     * {@code mod_dttm} 만 올린다 (§3.10.4 "mod_dttm 갱신").
     *
     * <p>이미지만 교체한 수정은 {@code notices} 의 어떤 칼럼도 바꾸지 않아 더티 체크에
     * 걸리지 않고, 그러면 {@code BaseTimeEntity} 의 {@code @PreUpdate} 가 돌지 않아
     * <b>수정 시각이 그대로 남는다</b>. 값 하나를 명시적으로 올린다.
     *
     * <p>벌크 UPDATE 라 두 플래그가 함께 필요하다 — {@code flushAutomatically} 가 없으면
     * 같은 트랜잭션에서 방금 바꾼 본문이 clear 로 버려진다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Notice n set n.modDttm = :now where n.noticeSeq = :noticeSeq")
    int touch(@Param("noticeSeq") Integer noticeSeq, @Param("now") Instant now);
}
