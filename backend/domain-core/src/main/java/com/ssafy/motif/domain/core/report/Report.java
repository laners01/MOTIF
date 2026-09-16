package com.ssafy.motif.domain.core.report;

import com.ssafy.motif.common.jpa.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 신고 (DB SPEC v0.15 §26, changeSet 0008-01).
 *
 * <p><b>대상 네 칸 중 정확히 하나만 채워진다</b>({@code ck_reports_exactly_one_target}) —
 * 참여 영상(§8.7)과 댓글 3종(§9.6)이 한 테이블을 쓴다. 나머지 칸이 NULL 이라
 * 1인 1대상 1회 UNIQUE 4종이 서로 간섭하지 않는다(Postgres 기본 NULLS DISTINCT).
 *
 * <p><b>생성자가 없다.</b> 접수는 {@link ReportRepository#insertVideoReport} 네이티브
 * INSERT 가 하고(왜인지는 그쪽 javadoc), 여기서는 읽기만 한다. 처리 상태를 옮기는 것은
 * 어드민 몫이라 이 티켓 범위 밖이다.
 *
 * @see ReportStatus 접수는 언제나 {@code RECEIVED} 로 시작한다
 */
@Entity
@Table(name = "reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_seq")
    private Long reportSeq;

    @Column(name = "reporter_seq", nullable = false, updatable = false)
    private Integer reporterSeq;

    /** 참여 영상 신고(§8.7)일 때만 채워진다. */
    @Column(name = "video_seq", updatable = false)
    private Long videoSeq;

    @Column(name = "challenge_comment_seq", updatable = false)
    private Long challengeCommentSeq;

    @Column(name = "video_comment_seq", updatable = false)
    private Long videoCommentSeq;

    @Column(name = "notice_comment_seq", updatable = false)
    private Long noticeCommentSeq;

    /**
     * 신고 사유.
     *
     * <p><b>코드표가 정해지지 않았다</b>(API 명세 §16 #4 — §8.7·§9.6·§13.3 공통 미결).
     * 그래서 enum 이 아니라 문자열이다. 값을 지금 열거로 고정하면 회의가 표를 정할 때
     * 이미 쌓인 행이 열거에 없는 값을 갖게 된다.
     */
    @Column(name = "reason_code", nullable = false, length = 30)
    private String reasonCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private ReportStatus status;

    @Column(name = "processed_usr_seq")
    private Integer processedUsrSeq;

    @Column(name = "processed_dttm")
    private Instant processedDttm;
}
