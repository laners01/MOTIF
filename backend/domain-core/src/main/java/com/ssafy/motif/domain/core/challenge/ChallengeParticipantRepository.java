package com.ssafy.motif.domain.core.challenge;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChallengeParticipantRepository
        extends JpaRepository<ChallengeParticipant, ChallengeParticipantId> {

    /** 마스크 오름차순 = 화면 좌→우 순서다({@code 1 << index}). */
    List<ChallengeParticipant> findByIdChallengeSeqOrderByParticipantMask(Integer challengeSeq);

    /**
     * 슬롯 구성 전체 교체의 하드 삭제 (§3.7.5, DB §13).
     *
     * <p><b>호출부는 이 뒤에 반드시 {@code flush()} 한다.</b> Hibernate 는 한 flush 안에서
     * INSERT 를 DELETE 보다 먼저 내보내므로, 지우고 바로 같은 마스크로 다시 넣으면
     * {@code uq_challenge_participants_mask} 에 걸린다 — 지운 뒤라서 안 걸릴 것 같지만
     * DB 가 보는 순서는 반대다.
     */
    void deleteByIdChallengeSeq(Integer challengeSeq);
}
