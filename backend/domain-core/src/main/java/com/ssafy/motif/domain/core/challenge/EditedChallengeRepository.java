package com.ssafy.motif.domain.core.challenge;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EditedChallengeRepository extends JpaRepository<EditedChallenge, Integer> {

    List<EditedChallenge> findByChallengeSeqOrderByParticipantMask(Integer challengeSeq);

    /**
     * 사용자가 고른 슬롯 조합의 편집본 (API 명세 §13.1).
     *
     * <p>{@code participantMask} 는 <b>비트 OR 합</b>이라 슬롯 0·1 을 함께 고르면 {@code 3}
     * 이다 — 단일 비트가 아니다({@link EditedChallenge}). {@code uq_edited_challenges_mask}
     * 가 (챌린지, 조합)의 유일성을 보장한다.
     *
     * <p><b>빈 결과는 운영 전제가 깨진 것이다.</b> 어드민이 {@code 1 … 2ⁿ−1} 전조합을
     * 등록하게 돼 있어(§3.7.2), 사용자는 촬영 직전에 {@code EDITED_CHALLENGE_NOT_FOUND} 를
     * 만나면 안 된다.
     *
     * <p>{@code mask=0}(무교체)으로는 부르지 않는다 — 그 조합에는 행이 없고 원본 위에
     * 합성한다. 호출부가 먼저 가른다.
     */
    Optional<EditedChallenge> findByChallengeSeqAndParticipantMask(Integer challengeSeq,
                                                                   int participantMask);

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
