package com.ssafy.motif.domain.core.challenge;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface EditedChallengeRepository extends JpaRepository<EditedChallenge, Integer> {

    List<EditedChallenge> findByChallengeSeqOrderByParticipantMask(Integer challengeSeq);

    /** 슬롯 구성 전체 교체의 하드 삭제 (§3.7.5) — 호출부의 {@code flush()} 필요 이유는 {@link ChallengeParticipantRepository} 참고. */
    void deleteByChallengeSeq(Integer challengeSeq);

    /**
     * 합성 잡이 참조 중인 편집본 seq (§3.7.5 {@code EDITED_IN_USE}).
     *
     * <p>FK 가 {@code ON DELETE RESTRICT} 라 그냥 지우면 DB 예외가 난다. 먼저 물어보고
     * 409 + 참조 seq 목록으로 응답한다 — 화면이 "참여가 시작돼 슬롯을 못 바꾼다"를
     * 설명할 수 있어야 한다.
     */
    @Query(value = """
            select ec.edited_challenge_seq from motif_core.edited_challenges ec
            where ec.challenge_seq = :challengeSeq
              and exists (select 1 from motif_core.composite_jobs j
                          where j.edited_challenge_seq = ec.edited_challenge_seq)
            order by ec.edited_challenge_seq
            """, nativeQuery = true)
    List<Integer> findSeqsReferencedByJobs(@Param("challengeSeq") Integer challengeSeq);

    /** 편집본별 잡 참조 건수 (§3.7.3 {@code jobRefCount}). 참조가 0 인 편집본은 행이 없다. */
    @Query(value = """
            select j.edited_challenge_seq as editedChallengeSeq, count(*) as jobCount
            from motif_core.composite_jobs j
            where j.edited_challenge_seq in (:editedChallengeSeqs)
            group by j.edited_challenge_seq
            """, nativeQuery = true)
    List<JobRefCount> countJobRefs(@Param("editedChallengeSeqs") Collection<Integer> editedChallengeSeqs);

    interface JobRefCount {
        Integer getEditedChallengeSeq();

        long getJobCount();
    }
}
