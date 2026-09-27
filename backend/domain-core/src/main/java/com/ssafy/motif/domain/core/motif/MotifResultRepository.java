package com.ssafy.motif.domain.core.motif;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
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

    /**
     * 합성 잡의 결과물 (API 명세 §13.3 MOTIF 분기).
     *
     * <p>§13.3 은 URL 을 싣지 않고 {@code resultSeq} 만 준다 — 재생과 문구 입력은
     * §11.6·§11.8 이 담당하므로 보관함 상세로 보낸다(R8 확정).
     *
     * <p><b>{@code DONE} 이면 항상 있다.</b> 결과 행 생성이 실패하면 잡을 {@code DONE} 으로
     * 만들지 않기 때문이다(§13.3 "MOTIF 잡의 완료 순서"). {@code uq_motif_results_job} 이
     * 잡당 한 행을 보장한다.
     *
     * <p>{@code is_deleted} 를 보지 않는다 — 사용자가 결과물을 지웠어도 그 잡이 성공했다는
     * 사실은 그대로다. 지워진 것을 §11.6 이 404 로 답하는 것은 그쪽 몫이다.
     */
    Optional<MotifResult> findByJobSeq(Long jobSeq);

    /*
     * ── 보관함 목록 (API 명세 §11.5) — 커서 페이지, 최신순 ─────────────────────
     *
     * 커서 유무 × artistSeq 유무로 메서드가 넷이다. 한 쿼리에 "null 이면 전체" 분기를
     * 넣지 않는 이유는 §5.5 공지 목록과 같다 — 타입 없는 null 을 바인딩하면 Postgres 가
     * 파라미터 타입을 추론하지 못해 운영에서 500 이 난다. 파생 메서드는 그 null 이 아예
     * 쿼리에 닿지 않게 한다.
     *
     * 정렬 키는 result_seq 다. reg_dttm 은 같은 초에 두 건이 만들어지면 페이지 경계가
     * 흔들린다 — 커서가 막으려는 것이 바로 그 흔들림이다.
     *
     * 전부 본인 것(userSeq)과 삭제분 제외(deletedFalse)를 건다. 남의 보관함이 섞이는
     * 경로를 메서드 이름으로 막아 둔다.
     *
     * 한 페이지에 limit+1 을 읽어 hasNext 를 판정하는 것은 호출부의 몫이다(§5.5 와 같다).
     */

    /** 최초 호출 — 커서 없음, 전체 아티스트. */
    List<MotifResult> findByUserSeqAndDeletedFalseOrderByResultSeqDesc(
            Integer userSeq, Pageable pageable);

    /** 이어받기 — 전체 아티스트. */
    List<MotifResult> findByUserSeqAndDeletedFalseAndResultSeqLessThanOrderByResultSeqDesc(
            Integer userSeq, Long resultSeq, Pageable pageable);

    /** 최초 호출 — 아티스트 필터(§5.8 진입점). */
    List<MotifResult> findByUserSeqAndArtistSeqAndDeletedFalseOrderByResultSeqDesc(
            Integer userSeq, Integer artistSeq, Pageable pageable);

    /** 이어받기 — 아티스트 필터. */
    List<MotifResult> findByUserSeqAndArtistSeqAndDeletedFalseAndResultSeqLessThanOrderByResultSeqDesc(
            Integer userSeq, Integer artistSeq, Long resultSeq, Pageable pageable);

    /**
     * 어드민 결과물 목록 (어드민 명세 §3.17.1) — 전 사용자, 삭제분도 본다.
     *
     * <p>{@code :x is null or} 분기는 {@code ParticipationVideoRepository.searchForAdmin}
     * 과 같다. 전부 정수·불리언이라 타입 없는 null 문제(위 보관함 주석)가 없다.
     */
    @Query("""
            select new com.ssafy.motif.domain.core.motif.AdminMotifResultRow(
                       r.resultSeq, r.userSeq, u.nickname, r.artistSeq,
                       r.assetSeq, s.title, r.description,
                       r.videoKey, r.thumbnailKey, r.durationMs,
                       r.deleted, r.regDttm)
            from MotifResult r
              join User u on u.userSeq = r.userSeq
              join MotifAsset s on s.assetSeq = r.assetSeq
            where (:userSeq is null or r.userSeq = :userSeq)
              and (:artistSeq is null or r.artistSeq = :artistSeq)
              and (:assetSeq is null or r.assetSeq = :assetSeq)
              and (:includeDeleted = true or r.deleted = false)
            order by r.resultSeq desc
            """)
    Page<AdminMotifResultRow> searchForAdmin(@Param("userSeq") Integer userSeq,
                                             @Param("artistSeq") Integer artistSeq,
                                             @Param("assetSeq") Integer assetSeq,
                                             @Param("includeDeleted") boolean includeDeleted,
                                             Pageable pageable);
}
