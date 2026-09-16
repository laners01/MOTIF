package com.ssafy.motif.coreapi.domain.report;

import com.ssafy.motif.common.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 신고 도메인 에러 코드 (API 명세 §15 표).
 *
 * <p><b>{@code VideoErrorCode} 에 두지 않는다.</b> §15 표가 {@code ALREADY_REPORTED} 를
 * §8.7(영상)·§9.6(댓글) 공용으로 적었다 — 영상 도메인에 두면 §9 댓글 신고가 남의
 * 도메인 코드를 참조하게 된다. code 는 전역 유일해야 하므로 두 API 가 함께 쓰는 것은
 * 신고 도메인이 소유한다({@code ChallengeErrorCode} 를 어드민이 참조하는 것과 같은 방식).
 */
@Getter
@RequiredArgsConstructor
public enum ReportErrorCode implements ErrorCode {

    /**
     * 1인 1대상 1회 (API 명세 §8.7 · §9.6).
     *
     * <p><b>처리 결과와 무관하다.</b> 기각된 신고도 재신고를 막는다 — 판정이 마음에
     * 들지 않는다고 같은 대상을 다시 올리는 길을 열지 않는다는 뜻이고, DB 의
     * {@code uq_reports_reporter_video} 가 상태를 보지 않는 이유다.
     */
    ALREADY_REPORTED(409, "ALREADY_REPORTED", "이미 신고한 대상입니다.");

    private final int status;
    private final String code;
    private final String message;
}
