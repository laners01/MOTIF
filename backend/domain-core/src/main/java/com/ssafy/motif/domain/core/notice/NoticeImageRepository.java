package com.ssafy.motif.domain.core.notice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface NoticeImageRepository extends JpaRepository<NoticeImage, Integer> {

    List<NoticeImage> findByNoticeSeqOrderByDisplayOrderAsc(Integer noticeSeq);

    /**
     * 팬 앱 공지 목록의 {@code images[]} (API 명세 §5.5) — 한 페이지 분을 한 번에.
     *
     * <p>§5.5 는 목록 항목마다 이미지 <b>전체 배열</b>을 요구한다(대표 이미지 전용
     * {@code repImageUrl} 은 쓰지 않는다). 공지마다 위 단건 메서드를 부르면 한 페이지에
     * 20번이라 N+1 이다 — 어드민 목록이 개수만 필요해
     * {@link #countByNoticeSeqs} 로 푼 자리를, 여기서는 행 자체가 필요하니 통째로 받아
     * 호출부가 {@code noticeSeq} 로 묶는다.
     *
     * <p>정렬에 {@code noticeSeq} 를 먼저 넣는 것은 그룹핑 편의가 아니라 <b>결정성</b>
     * 때문이다 — {@code display_order} 만으로 정렬하면 공지가 다른 행끼리의 순서가
     * 정해지지 않는다. 항목 안의 순서는 {@code displayOrder} 오름차순 그대로다(§5.5).
     */
    List<NoticeImage> findByNoticeSeqInOrderByNoticeSeqAscDisplayOrderAsc(
            Collection<Integer> noticeSeqs);

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
