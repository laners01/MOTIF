package com.ssafy.motif.domain.core.report;

/**
 * 신고 처리 상태 (DB SPEC v0.15 §26, {@code ck_reports_status}).
 *
 * <p>접수는 언제나 {@link #RECEIVED} 로 시작한다 — {@code ck_reports_processed} 가
 * 처리자·처리 시각이 <b>비어 있을 것</b>을 함께 요구하므로, 접수 시점에 상태를 다른
 * 값으로 넣을 방법이 없다.
 *
 * <p>{@link #ACCEPTED}·{@link #REJECTED} 로 옮기는 것은 어드민 신고 처리 몫이다 —
 * 사용자 API(§8.7·§9.6)는 접수만 한다.
 */
public enum ReportStatus {

    /** 접수됨 — 처리자·처리 시각이 NULL 인 상태. */
    RECEIVED,

    /** 신고가 받아들여짐. */
    ACCEPTED,

    /** 신고가 기각됨. */
    REJECTED
}
