package com.ssafy.motif.domain.core.video;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ParticipationVideoRepository extends JpaRepository<ParticipationVideo, Long> {

    /**
     * 마이페이지 3×3 프리뷰 (API 명세 §4.1) — 본인 영상 최신순.
     *
     * <p><b>{@code PRIVATE} 도 포함한다.</b> 본인 화면이라 둘 다 보여 주고 항목에
     * {@code visibility} 를 실어 클라이언트가 자물쇠를 그린다.
     *
     * <p><b>삭제된 챌린지에 속한 영상은 뺀다</b>(§1.8 공통 비노출) — 영상 자체가
     * 살아 있어도 그렇다. 연관 매핑이 없어 {@code exists} 로 건다: 참여 영상은 챌린지를
     * 통해 아무것도 읽지 않으므로 {@code @ManyToOne} 을 두면 쓰지도 않을 조인이
     * 따라다닌다.
     *
     * <p>{@code reg_dttm} 이 아니라 PK 역순으로 정렬한다 — IDENTITY 라 단조 증가해
     * 최신순과 결과가 같은데, 같은 초에 올라온 영상들이 호출마다 순서를 바꿔 가며
     * 나오는 일이 없다({@code ChallengeRepository} 와 같은 근거).
     *
     * <p>건수 상한은 {@link Pageable} 로 받는다 — 9 라는 값은 화면 사정(3×3)이라
     * 리포지토리가 아니라 서비스가 갖는다.
     */
    @Query("""
            select v from ParticipationVideo v
            where v.userSeq = :userSeq
              and v.deleted = false
              and exists (select 1 from Challenge c
                           where c.challengeSeq = v.challengeSeq and c.deleted = false)
            order by v.videoSeq desc
            """)
    List<ParticipationVideo> findMyPreview(@Param("userSeq") Integer userSeq, Pageable pageable);

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
