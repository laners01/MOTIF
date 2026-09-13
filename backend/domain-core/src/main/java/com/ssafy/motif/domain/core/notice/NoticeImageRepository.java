package com.ssafy.motif.domain.core.notice;

import org.springframework.data.jpa.repository.JpaRepository;

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
}
