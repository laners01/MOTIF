package com.ssafy.motif.domain.core.job;

/**
 * 합성 잡의 종류 ({@code composite_jobs.job_type} — DB SPEC v0.15 §37).
 *
 * <p>산출물 프리픽스의 두 번째 세그먼트가 이 값에서 나온다
 * ({@code private/challenge/…} · {@code private/motif/…}) — 규약 자체는
 * {@code MediaKeys} 가 소유하므로 여기서 경로를 만들지 않는다.
 *
 * <p>DDL 의 {@code ck_composite_jobs_type_shape} 가 타입별로 채워야 할 칸을 다르게
 * 강제한다 — {@code CHALLENGE} 는 {@code challenge_seq}, {@code MOTIF} 는
 * {@code asset_seq} 다. 값을 늘리려면 CHECK 제약도 함께 고쳐야 한다.
 */
public enum JobType {

    /** 챌린지 참여 영상 합성. 게시(§8.1)되면 {@code participation_videos} 행이 생긴다. */
    CHALLENGE,

    /** 모티프 결과물 합성. 성공하면 {@code motif_results} 행이 생긴다(보관함). */
    MOTIF
}
