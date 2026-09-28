package com.ssafy.motif.domain.core.job;

/**
 * 합성 잡의 상태 ({@code composite_jobs.status} — DB SPEC v0.15 §37).
 *
 * <p><b>{@link #DONE} 만 산출물 키를 갖는다.</b> DDL 의
 * {@code ck_composite_jobs_status_shape} 가 그것을 강제한다 —
 * {@code DONE} 이면 {@code result_video_key}·{@code result_thumbnail_key} 가 NOT NULL,
 * {@code FAILED} 면 {@code fail_code} 가 NOT NULL, 나머지는 셋 다 NULL 이다.
 * 그래서 재생목록을 내주기 전에 이 값을 먼저 본다 — {@code PROCESSING} 잡의 키를
 * 읽으려 하면 NULL 이다.
 */
public enum JobStatus {

    /** 원본 업로드 완료. 아직 큐에 넣지 않았다. */
    UPLOADED,

    /** {@code job.media} 발행됨. 워커가 집기를 기다린다. */
    QUEUED,

    /** 워커가 처리 중. */
    PROCESSING,

    /** 성공. 산출물 키가 채워져 있다. */
    DONE,

    /** 실패. {@code fail_code} 가 채워져 있다. */
    FAILED
}
