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
     * <p><b>네이티브다.</b> {@code notice_likes} 는 아직 엔티티가 없고 좋아요 토글(§10.2)
     * 티켓이 소유할 테이블이라 미리 매핑하지 않는다
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
