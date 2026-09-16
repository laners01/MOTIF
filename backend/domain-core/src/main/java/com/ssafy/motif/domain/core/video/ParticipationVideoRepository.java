package com.ssafy.motif.domain.core.video;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ParticipationVideoRepository extends JpaRepository<ParticipationVideo, Long> {

    /**
     * 보호 등급 인가 — 이 잡이 게시됐는지, 됐다면 공개 범위가 무엇인지 (API 명세 §1.7).
     *
     * <p><b>삭제분도 가져온다.</b> 여기서 {@code is_deleted=false} 를 걸면 삭제된 영상이
     * "게시된 적 없음"과 같아져 <b>소유자 전용</b> 분기로 떨어지는데, 그쪽이 더 느슨하다
     * — 삭제한 PUBLIC 영상의 재생목록을 소유자에게 계속 내주게 된다. 삭제 판단은
     * 호출자가 따로 한다.
     */
    Optional<ParticipationVideo> findByJobSeq(Long jobSeq);
}
