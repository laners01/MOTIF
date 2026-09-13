package com.ssafy.motif.domain.core.challenge;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;

public interface ChallengeParticipantRepository
        extends JpaRepository<ChallengeParticipant, ChallengeParticipantId> {

    /** 마스크 오름차순 = 화면 좌→우 순서다({@code 1 << index}). */
    List<ChallengeParticipant> findByIdChallengeSeqOrderByParticipantMask(Integer challengeSeq);

    /**
     * 슬롯 구성 전체 교체의 하드 삭제 (§3.7.5, DB §13).
     *
     * <p>{@code flushAutomatically} 없이 두면 같은 트랜잭션에서 뒤이어 INSERT 하는
     * 새 슬롯이 DELETE 보다 먼저 flush 될 수 있어 {@code uq_..._mask} 와 충돌한다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    void deleteByIdChallengeSeq(Integer challengeSeq);
}
