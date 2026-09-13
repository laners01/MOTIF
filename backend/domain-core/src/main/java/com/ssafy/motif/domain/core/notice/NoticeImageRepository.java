package com.ssafy.motif.domain.core.notice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface NoticeImageRepository extends JpaRepository<NoticeImage, Integer> {

    List<NoticeImage> findByNoticeSeqOrderByDisplayOrderAsc(Integer noticeSeq);

    /**
     * 이미지 전체 교체의 앞 절반 — 하드 삭제 (DB §28).
     *
     * <p>파생 삭제 쿼리는 행을 읽어 하나씩 지운다. 장수가 10 이하라(§3.10.2) 왕복이
     * 문제되지 않고, 영속성 컨텍스트가 같이 정리돼 <b>바로 뒤따르는 재생성이
     * {@code uq_notice_images_order} 와 충돌하지 않는다</b> — 벌크 delete 였다면
     * 삭제 전 상태가 컨텍스트에 남아 flush 순서가 어긋난다.
     */
    void deleteByNoticeSeq(Integer noticeSeq);

    /**
     * 목록의 {@code imageCount} (어드민 명세 §3.10.1) — 공지당 장수를 한 번에 센다.
     *
     * <p>행을 끌어오지 않는다. 목록 한 페이지(최대 100건)마다 공지별로 이미지를 조회하면
     * N+1 이 되는데, 목록에 필요한 것은 개수 하나뿐이다.
     *
     * <p><b>이미지가 0 장인 공지는 결과에 행이 없다</b> — 호출부가 기본값 0 을 쓴다
     * ({@code EditedChallengeRepository.countJobRefs} 와 같은 규칙).
     */
    @Query(value = """
            select i.notice_seq as noticeSeq, count(*) as imageCount
            from motif_core.notice_images i
            where i.notice_seq in (:noticeSeqs)
            group by i.notice_seq
            """, nativeQuery = true)
    List<ImageCount> countByNoticeSeqs(@Param("noticeSeqs") Collection<Integer> noticeSeqs);

    interface ImageCount {
        Integer getNoticeSeq();

        long getImageCount();
    }
}
