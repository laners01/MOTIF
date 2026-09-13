package com.ssafy.motif.domain.core.challenge;

import com.ssafy.motif.domain.core.artist.ArtistCount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ChallengeRepository extends JpaRepository<Challenge, Integer> {

    /** 아티스트 목록·상세의 {@code challengeCount} (어드민 §3.5.1) — 페이지 전체를 한 번에. */
    @Query("""
            select new com.ssafy.motif.domain.core.artist.ArtistCount(c.artistSeq, count(c))
            from Challenge c
            where c.artistSeq in :artistSeqs and c.deleted = false
            group by c.artistSeq
            """)
    List<ArtistCount> countByArtists(@Param("artistSeqs") Collection<Integer> artistSeqs);

    /** 아티스트 상세의 최신 챌린지 5건 (§3.5.3 {@code recentChallenges[]}). */
    List<Challenge> findTop5ByArtistSeqAndDeletedFalseOrderByChallengeSeqDesc(Integer artistSeq);

    /**
     * 팬 앱 아티스트 챌린지 그리드 (API 명세 §5.4) — <b>비페이지 전량</b>, 최신순.
     *
     * <p>페이지를 두지 않는 것은 회의 확정(R2)이다. 어드민 목록(§3.7.1)과 달리 필터가
     * 없고 한 아티스트의 챌린지는 운영상 수십 건 규모라, 그리드가 한 번에 받는다.
     *
     * <p>{@code reg_dttm} 이 아니라 PK 역순으로 정렬한다 — IDENTITY 라 단조 증가라서
     * 최신순과 결과가 같은데, 같은 초에 등록된 챌린지들이 호출마다 순서를 바꿔 가며
     * 나오는 일이 없다({@code NoticeRepository.findAdminPage} 와 같은 근거).
     *
     * <p>삭제된 챌린지는 뺀다 (§1.8 공통 비노출).
     */
    List<Challenge> findByArtistSeqAndDeletedFalseOrderByChallengeSeqDesc(Integer artistSeq);

    /**
     * 챌린지 상세의 {@code isLiked} (API 명세 §7.2).
     *
     * <p><b>네이티브다.</b> {@code challenge_likes} 는 아직 엔티티가 없고 여기서 필요한
     * 것은 참·거짓 하나뿐이라, 좋아요 토글(§7.3) 티켓이 소유할 테이블을 미리 매핑하지
     * 않는다 — {@link #countActiveJobs} 와 같은 규칙이다. 그 티켓이 엔티티를 만들면
     * 이 쿼리는 그리로 옮긴다.
     */
    @Query(value = """
            select exists(
                select 1 from motif_core.challenge_likes
                 where challenge_seq = :challengeSeq and user_seq = :userSeq)
            """, nativeQuery = true)
    boolean isLikedBy(@Param("challengeSeq") Integer challengeSeq, @Param("userSeq") Integer userSeq);

    /** 어드민도 삭제된 챌린지는 수정 대상으로 잡지 않는다 — §3.7 실패표 404 "대상 없음·삭제". */
    Optional<Challenge> findByChallengeSeqAndDeletedFalse(Integer challengeSeq);

    /**
     * 어드민 목록 (어드민 명세 §3.7.1) — 아티스트·곡 필터, offset 페이지.
     *
     * <p><b>필터는 {@code null} 이면 없는 셈 친다.</b> 조합마다 파생 메서드를 만들면
     * 2×2 = 4개가 되고, 필터가 하나 늘 때마다 배가 된다.
     *
     * <p>목록은 <b>삭제된 챌린지를 기본으로 감춘다</b>({@code includeDeleted=false}).
     * 소프트 삭제는 앱 비노출이지 이력 삭제가 아니라, 운영자가 지운 것을 다시 볼
     * 수단은 남겨 둔다.
     *
     * <p>count 쿼리에 조인이 없는 것은 의도다 — {@code artist_seq}·{@code song_seq} 가
     * NOT NULL FK 라 조인이 행을 떨어뜨리지 않는다. 세는 데 3번 조인할 이유가 없다.
     */
    @Query(value = """
            select new com.ssafy.motif.domain.core.challenge.ChallengeListRow(
                   c.challengeSeq, c.artistSeq, u.nickname,
                   s.songSeq, s.title, s.singerName,
                   c.thumbnailKey, c.durationMs,
                   (select count(p) from ChallengeParticipant p where p.id.challengeSeq = c.challengeSeq),
                   (select count(e) from EditedChallenge e where e.challengeSeq = c.challengeSeq),
                   c.viewCount, c.participantCount, c.likeCount, c.commentCount,
                   c.deleted, c.regDttm)
              from Challenge c, Artist a, User u, Song s
             where a.artistSeq = c.artistSeq and u.userSeq = a.userSeq and s.songSeq = c.songSeq
               and (:artistSeq is null or c.artistSeq = :artistSeq)
               and (:songSeq is null or c.songSeq = :songSeq)
               and (:includeDeleted = true or c.deleted = false)
             order by c.challengeSeq desc
            """,
            countQuery = """
            select count(c) from Challenge c
             where (:artistSeq is null or c.artistSeq = :artistSeq)
               and (:songSeq is null or c.songSeq = :songSeq)
               and (:includeDeleted = true or c.deleted = false)
            """)
    Page<ChallengeListRow> search(@Param("artistSeq") Integer artistSeq,
                                  @Param("songSeq") Integer songSeq,
                                  @Param("includeDeleted") boolean includeDeleted,
                                  Pageable pageable);

    /**
     * 진행 중 합성 잡 수 (§3.7.3 {@code activeJobCount}).
     *
     * <p><b>네이티브다.</b> {@code composite_jobs} 는 아직 엔티티가 없고, 여기서 필요한 것은
     * 숫자 하나뿐이라 CHECK 제약이 얽힌 테이블을 통째로 매핑할 이유가 없다. 잡 도메인
     * 티켓이 엔티티를 만들면 그때 JPQL 로 바꾼다.
     */
    @Query(value = """
            select count(*) from motif_core.composite_jobs
            where challenge_seq = :challengeSeq and status in ('QUEUED', 'PROCESSING')
            """, nativeQuery = true)
    long countActiveJobs(@Param("challengeSeq") Integer challengeSeq);
}
