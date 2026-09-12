package com.ssafy.motif.common.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 도메인에 속하지 않는 공통 에러 코드 — <b>API 명세 §1.4 표와 1:1</b>(§15 요구).
 *
 * <p>명세 §1.4 는 두 묶음을 공통으로 규정한다. ① 전 API 공통 분기
 * ({@code INVALID_PARAMETER}·{@code UNAUTHORIZED}·{@code FORBIDDEN}·
 * {@code NOT_FOUND}·{@code CONFLICT}·{@code INTERNAL_ERROR}) ② 도메인 공용 확장
 * ({@code ACCOUNT_SUSPENDED}·{@code ACCOUNT_DELETED}·{@code PAYMENT_FAILED}·
 * {@code RATE_LIMITED}). 둘 다 특정 도메인 enum 이 소유하면 같은 code 문자열이
 * 여러 곳에 생기므로 여기 모아 둔다.
 *
 * <p>{@code NOT_FOUND}·{@code CONFLICT} 는 일반형이다 — 도메인 코드
 * ({@code VIDEO_NOT_FOUND} 등)가 있으면 그쪽이 우선한다(명세 §1.4 비고).
 */
@Getter
@RequiredArgsConstructor
public enum CommonErrorCode implements ErrorCode {

    /** 형식·필수값 검증 실패. {@code data} 에 필드 오류 배열을 싣는다 (명세 §1.2 검증 오류). */
    INVALID_PARAMETER(400, "INVALID_PARAMETER", "요청 값이 올바르지 않습니다."),

    /** 토큰 없음·만료·위조 — 🔓 표시 API 를 제외한 전 API 공통 (명세 §1.5). */
    UNAUTHORIZED(401, "UNAUTHORIZED", "로그인이 필요합니다."),

    /** 소유권·권한 없음(일반). 예: USER 토큰으로 다른 사용자 리소스 수정. */
    FORBIDDEN(403, "FORBIDDEN", "권한이 없습니다."),

    /** 리소스 없음(일반). 도메인 코드({@code *_NOT_FOUND})가 있으면 그쪽을 쓴다. */
    NOT_FOUND(404, "NOT_FOUND", "대상을 찾을 수 없습니다."),

    /** 상태 충돌(일반). 도메인 코드가 있으면 그쪽을 쓴다. */
    CONFLICT(409, "CONFLICT", "현재 상태에서는 처리할 수 없습니다."),

    /**
     * {@code user_accounts.account_status='SUSPENDED'} — 전 API 공통 403.
     * 로그인·재발급 시점에도 검증한다 (명세 §1.5).
     */
    ACCOUNT_SUSPENDED(403, "ACCOUNT_SUSPENDED", "정지된 계정입니다."),

    /** {@code users.is_deleted=TRUE} — 전 API 공통 403 (명세 §1.5). */
    ACCOUNT_DELETED(403, "ACCOUNT_DELETED", "삭제된 계정입니다."),

    /** 토스 승인 실패 — 사용처는 §12.2. {@code data} 에 토스 실패 사유를 싣는다. */
    PAYMENT_FAILED(402, "PAYMENT_FAILED", "결제 승인에 실패했습니다."),

    /**
     * 레이트리밋 초과. 명세는 SCG 담당으로 적었지만 SCG 는 제거됐고(ADR-0009)
     * 앱 필터가 맡는다(ADR-0009 D4 — 구현은 후속 티켓). 계약은 동일하다.
     */
    RATE_LIMITED(429, "RATE_LIMITED", "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),

    /** 서버 오류 — 사유는 응답에 싣지 않고 로그에만 남긴다. */
    INTERNAL_ERROR(500, "INTERNAL_ERROR", "서버 내부 오류가 발생했습니다.");

    private final int status;
    private final String code;
    private final String message;
}
