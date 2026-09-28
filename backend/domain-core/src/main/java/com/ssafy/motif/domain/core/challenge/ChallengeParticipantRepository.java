package com.ssafy.motif.domain.core.challenge;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ChallengeParticipantRepository
        extends JpaRepository<ChallengeParticipant, Integer> {

    /** {@code slotOrder} 오름차순 = 화면 좌→우 순서다. */
    List<ChallengeParticipant> findByChallengeSeqOrderBySlotOrderAsc(Integer challengeSeq);

    /**
     * 사용자가 고른 슬롯 (API 명세 §13.1).
     *
     * <p>{@code uq_challenge_participants_slot_order} 가 (챌린지, 순서)의 유일성을 보장한다.
     * 호출부는 <b>편집본이 있는지</b>({@link ChallengeParticipant#hasEdited()})를 따로 본다 —
     * 슬롯은 있는데 편집본을 아직 안 올린 상태가 정상적으로 존재하기 때문이다(0019).
     */
    Optional<ChallengeParticipant> findByChallengeSeqAndSlotOrder(Integer challengeSeq, int slotOrder);

    /** 어드민 상세의 앵커 단독 수정(§3.7.9) — 아티스트로 슬롯을 찾는다. */
    Optional<ChallengeParticipant> findByChallengeSeqAndArtistSeq(Integer challengeSeq, Integer artistSeq);

    /**
     * 슬롯 구성 전체 교체의 하드 삭제 (§3.7.5, DB §13).
     *
     * <p><b>호출부는 이 뒤에 반드시 {@code flush()} 한다.</b> Hibernate 는 한 flush 안에서
     * INSERT 를 DELETE 보다 먼저 내보내므로, 지우고 바로 같은 순서로 다시 넣으면
     * {@code uq_challenge_participants_slot_order} 에 걸린다 — 지운 뒤라서 안 걸릴 것 같지만
     * DB 가 보는 순서는 반대다.
     */
    void deleteByChallengeSeq(Integer challengeSeq);

    /**
     * 합성 잡이 참조 중인 슬롯 seq (§3.7.5 {@code EDITED_IN_USE}).
     *
     * <p>FK 가 {@code ON DELETE RESTRICT} 라 그냥 지우면 DB 예외가 난다. 먼저 물어보고
     * 409 + 참조 seq 목록으로 응답한다 — 화면이 "참여가 시작돼 슬롯을 못 바꾼다"를
     * 설명할 수 있어야 한다.
     */
    @Query(value = """
            select p.challenge_participant_seq from motif_core.challenge_participants p
            where p.challenge_seq = :challengeSeq
              and exists (select 1 from motif_core.composite_jobs j
                          where j.challenge_participant_seq = p.challenge_participant_seq)
            order by p.challenge_participant_seq
            """, nativeQuery = true)
    List<Integer> findSeqsReferencedByJobs(@Param("challengeSeq") Integer challengeSeq);

    /** 슬롯별 잡 참조 건수 (§3.7.3 {@code jobRefCount}). 참조가 0 인 슬롯은 행이 없다. */
    @Query(value = """
            select j.challenge_participant_seq as challengeParticipantSeq, count(*) as jobCount
            from motif_core.composite_jobs j
            where j.challenge_participant_seq in (:challengeParticipantSeqs)
            group by j.challenge_participant_seq
            """, nativeQuery = true)
    List<JobRefCount> countJobRefs(
            @Param("challengeParticipantSeqs") Collection<Integer> challengeParticipantSeqs);

    interface JobRefCount {
        Integer getChallengeParticipantSeq();

        long getJobCount();
    }
}
