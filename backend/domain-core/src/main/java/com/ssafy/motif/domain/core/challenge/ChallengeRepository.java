package com.ssafy.motif.domain.core.challenge;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ChallengeRepository extends JpaRepository<Challenge, Integer> {

    /** 어드민도 삭제된 챌린지는 수정 대상으로 잡지 않는다 — §3.7 실패표 404 "대상 없음·삭제". */
    Optional<Challenge> findByChallengeSeqAndDeletedFalse(Integer challengeSeq);

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
