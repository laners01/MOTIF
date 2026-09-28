package com.ssafy.motif.domain.core.notice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공지 이미지 (DB SPEC v0.15 §28, changeSet 0007-02).
 *
 * <p><b>부분 수정이 없다.</b> 이미지 교체는 이 공지의 행을 전부 하드 삭제하고 새 순서로
 * 다시 만든다(DB §28, 어드민 명세 §3.10.4). {@code uq_notice_images_order} 가
 * {@code (notice_seq, display_order)} 를 잠그고 있어 "3번을 2번으로" 같은 개별 이동은
 * 중간 상태에서 반드시 충돌하는데, 전체 교체는 그 문제가 아예 없다.
 *
 * <p>공지를 소프트 삭제해도 이 행은 남는다(§3.10.5).
 */
@Entity
@Table(name = "notice_images")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NoticeImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notice_image_seq")
    private Integer noticeImageSeq;

    @Column(name = "notice_seq", nullable = false)
    private Integer noticeSeq;

    @Column(name = "image_key", nullable = false)
    private String imageKey;

    @Column(name = "display_order", nullable = false)
    private short displayOrder;

    public static NoticeImage of(Integer noticeSeq, String imageKey, short displayOrder) {
        NoticeImage image = new NoticeImage();
        image.noticeSeq = noticeSeq;
        image.imageKey = imageKey;
        image.displayOrder = displayOrder;
        return image;
    }
}
