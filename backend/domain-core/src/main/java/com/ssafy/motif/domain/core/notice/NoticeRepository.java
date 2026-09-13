package com.ssafy.motif.domain.core.notice;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface NoticeRepository extends JpaRepository<Notice, Integer> {

    /**
     * 어드민 목록 (어드민 명세 §3.10.1) — 아티스트 필터 · 삭제분 포함 여부 · 최신순.
     *
     * <p><b>정렬은 서버가 고정한다</b>(§1.5 "서버 고정 정렬 엔드포인트는 sort 무시").
     * {@code reg_dttm} 이 아니라 PK 역순인 이유: IDENTITY 라 단조 증가라서 최신순과
     * 같은 결과인데, 같은 초에 등록된 공지들이 순서를 바꿔 가며 나오는 일이 없다 —
     * 페이지를 넘길 때 행이 중복되거나 건너뛰어지지 않는다.
     *
     * <p>파라미터 두 개가 {@code null}·{@code true} 면 조건이 통째로 빠진다. 조합마다
     * 파생 메서드를 만들면 네 개가 되고, 필터가 하나 늘 때마다 두 배가 된다.
     *
     * <p>count 쿼리를 명시한다 — 파생 count 는 {@code order by} 를 떼어 내는 과정에서
     * 조건이 있는 쿼리를 잘못 다룰 여지가 있고, 여기서 틀리면 {@code totalPages} 만
     * 조용히 어긋난다.
     */
    @Query(value = """
            select n from Notice n
            where (:artistSeq is null or n.artistSeq = :artistSeq)
              and (:includeDeleted = true or n.deleted = false)
            order by n.noticeSeq desc
            """,
            countQuery = """
            select count(n) from Notice n
            where (:artistSeq is null or n.artistSeq = :artistSeq)
              and (:includeDeleted = true or n.deleted = false)
            """)
    Page<Notice> findAdminPage(@Param("artistSeq") Integer artistSeq,
                               @Param("includeDeleted") boolean includeDeleted,
                               Pageable pageable);

    /** 삭제된 공지는 없는 것으로 본다 — 수정·삭제 대상 조회(§3.10.4·§3.10.5). */
    Optional<Notice> findByNoticeSeqAndDeletedFalse(Integer noticeSeq);

    /**
     * {@code mod_dttm} 만 올린다 (§3.10.4 "mod_dttm 갱신").
     *
     * <p>이미지만 교체한 수정은 {@code notices} 의 어떤 칼럼도 바꾸지 않아 더티 체크에
     * 걸리지 않고, 그러면 {@code BaseTimeEntity} 의 {@code @PreUpdate} 가 돌지 않아
     * <b>수정 시각이 그대로 남는다</b>. 값 하나를 명시적으로 올린다.
     *
     * <p>벌크 UPDATE 라 두 플래그가 함께 필요하다 — {@code flushAutomatically} 가 없으면
     * 같은 트랜잭션에서 방금 바꾼 본문이 clear 로 버려진다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Notice n set n.modDttm = :now where n.noticeSeq = :noticeSeq")
    int touch(@Param("noticeSeq") Integer noticeSeq, @Param("now") Instant now);
}
