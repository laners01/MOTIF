package com.ssafy.motif.domain.core.challenge;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ChallengeRepository extends JpaRepository<Challenge, Integer> {

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
