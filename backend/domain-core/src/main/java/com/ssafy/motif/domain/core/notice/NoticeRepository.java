package com.ssafy.motif.domain.core.notice;

import com.ssafy.motif.domain.core.artist.ArtistCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Collection;
import java.time.Instant;
import java.util.Optional;

public interface NoticeRepository extends JpaRepository<Notice, Integer> {

    /** 아티스트 목록·상세의 {@code noticeCount} (어드민 §3.5.1) — 페이지 전체를 한 번에. */
    @Query("""
            select new com.ssafy.motif.domain.core.artist.ArtistCount(n.artistSeq, count(n))
            from Notice n
            where n.artistSeq in :artistSeqs and n.deleted = false
            group by n.artistSeq
            """)
    List<ArtistCount> countByArtists(@Param("artistSeqs") Collection<Integer> artistSeqs);

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
