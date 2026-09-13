package com.ssafy.motif.domain.core.notice;

import com.ssafy.motif.common.jpa.BaseTimeEntity;
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
 * 아티스트 공지 (DB SPEC v0.15 §27, changeSet 0007-01).
 *
 * <p><b>삭제는 소프트다.</b> 좋아요·댓글·이미지 행이 참조하고 있고 지표도 이 행에
 * 매달려 있어 물리 삭제하지 않는다 — {@code is_deleted} 만 올리고 자식 행은 그대로 둔다
 * (어드민 명세 §3.10.5).
 *
 * <p>{@code likeCount}·{@code commentCount} 는 거울 카운터라 좋아요·댓글 서비스가 원자적
 * UPDATE 로 증감한다. 공지 수정·삭제는 이 값을 건드리지 않는다.
 */
@Entity
@Table(name = "notices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notice extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notice_seq")
    private Integer noticeSeq;

    @Column(name = "artist_seq", nullable = false)
    private Integer artistSeq;

    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "like_count", nullable = false)
    private int likeCount;

    @Column(name = "comment_count", nullable = false)
    private int commentCount;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    /**
     * 어드민 작성 (어드민 명세 §3.10.2).
     *
     * <p><b>등록자를 행에 남기지 않는다.</b> {@code notices} 에는 {@code reg_usr_seq} 가
     * 없다(DB §27) — 누가 썼는지는 감사 로그가 갖는다({@code AdminAuditLogger}, §1.3).
     * 공지의 주체는 어드민이 아니라 {@code artist_seq} 의 아티스트다.
     *
     * <p>카운터·삭제 플래그는 DB 기본값(0·FALSE)과 같은 자바 기본값으로 시작한다.
     */
    public static Notice register(Integer artistSeq, String content) {
        Notice notice = new Notice();
        notice.artistSeq = artistSeq;
        notice.content = content;
        return notice;
    }

    public void changeContent(String content) {
        this.content = content;
    }

    public void softDelete() {
        this.deleted = true;
    }
}
