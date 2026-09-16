package com.ssafy.motif.domain.core.motif;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MotifResultRepository extends JpaRepository<MotifResult, Long> {

    /**
     * 마이페이지 대표 모티프 (API 명세 §4.1).
     *
     * <p><b>{@code userSeq} 를 함께 건다.</b> {@code users.rep_motif_result_seq} 는 FK 가
     * 없는 논리 참조라 남의 결과물 seq 가 들어가 있어도 DB 가 막지 않는다 — 그 상태에서
     * seq 만으로 조회하면 남의 보관함이 내 마이페이지에 뜬다.
     *
     * <p>삭제분도 뺀다. 가리키던 결과물이 지워졌으면 대표 모티프는 없는 것으로 답한다.
     */
    Optional<MotifResult> findByResultSeqAndUserSeqAndDeletedFalse(Long resultSeq, Integer userSeq);
}
