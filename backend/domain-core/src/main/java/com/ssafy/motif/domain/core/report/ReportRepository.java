package com.ssafy.motif.domain.core.report;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ReportRepository extends JpaRepository<Report, Long> {

    /**
     * 참여 영상 신고 접수 (API 명세 §8.7).
     *
     * <p><b>중복 판정을 DB 가 한다.</b> "이미 신고했는가"를 먼저 묻고 갈라지면 묻는
     * 시점과 쓰는 시점이 벌어져, 동시에 들어온 두 요청이 둘 다 통과한다 — 그러면
     * {@code uq_reports_reporter_video} 위반이 500 으로 터진다. {@code on conflict do
     * nothing} 은 확인과 삽입을 한 문장에서 끝내므로 <b>반환값이 곧 "내가 새로
     * 접수했는가"</b> 다({@code SubscriptionRepository.insertIfAbsent} 와 같은 방식).
     *
     * <p><b>엔티티로 저장하지 않는 이유가 하나 더 있다.</b> 대상 네 칸 중 하나만 채우는
     * XOR CHECK 가 걸려 있어 JPA 로 쓰면 나머지 세 칸을 명시적으로 비워야 하고, 그 규칙이
     * 엔티티 생성자에 복제된다. 접수 문장이 곧 규칙인 편이 낫다.
     *
     * <p>{@code status}·{@code reg_dttm} 을 넣지 않는 것은 DDL 에 기본값이 있기
     * 때문이다(0008-01: {@code 'RECEIVED'} · {@code CURRENT_TIMESTAMP}). 엔티티의
     * {@code @PrePersist} 는 네이티브라 타지 않는다.
     *
     * @return 1 이면 접수됐다. <b>0 이면 이미 신고한 대상</b>이라 호출부가 409 로 답한다
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            insert into motif_core.reports (reporter_seq, video_seq, reason_code)
            values (:reporterSeq, :videoSeq, :reasonCode)
            on conflict do nothing
            """, nativeQuery = true)
    int insertVideoReport(@Param("reporterSeq") Integer reporterSeq,
                          @Param("videoSeq") Long videoSeq,
                          @Param("reasonCode") String reasonCode);

    /** 접수된 신고를 되읽는다 — 지금은 테스트가 쓴다. */
    Optional<Report> findByReporterSeqAndVideoSeq(Integer reporterSeq, Long videoSeq);
}
