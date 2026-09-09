package com.ssafy.motif.coreapi.auth;

import com.ssafy.motif.common.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 인증 도메인 에러 코드 (API 명세 v0.11 §2).
 *
 * <p><b>형식 편차 — 리뷰 확인 필요.</b> {@link ErrorCode} javadoc과 팀 표준은
 * "영역 접두사 + 3자리 숫자"({@code A001} 등)를 규정하지만, API 명세 §2.1 실패
 * 예시는 {@code "code": "INVALID_GOOGLE_TOKEN"}처럼 이름 문자열을 계약으로 적고
 * 있다. 안드로이드가 이 값으로 분기하므로 <b>클라이언트 계약인 명세를 따랐다</b>.
 * 팀이 숫자 형식으로 확정하면 각 상수의 두 번째 인자만 바꾸면 된다.
 */
@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

    /** 구글 ID 토큰 검증 실패 — 만료·위조·aud 불일치 (명세 §2.1). */
    INVALID_GOOGLE_TOKEN(401, "INVALID_GOOGLE_TOKEN", "구글 인증에 실패했습니다. 다시 로그인해 주세요."),

    /** 서비스 access token 검증 실패 — 전 API 공통 401 (명세 §1.5). */
    UNAUTHORIZED(401, "UNAUTHORIZED", "로그인이 필요합니다."),

    /** {@code user_accounts.account_status='SUSPENDED'} (명세 §1.4 공통 확장). */
    ACCOUNT_SUSPENDED(403, "ACCOUNT_SUSPENDED", "정지된 계정입니다."),

    /** {@code users.is_deleted=TRUE} (명세 §1.4 공통 확장). */
    ACCOUNT_DELETED(403, "ACCOUNT_DELETED", "삭제된 계정입니다."),

    /**
     * 인증은 됐지만 권한이 부족하다(예: USER 토큰으로 {@code /admin} 접근).
     * <b>명세 §1.4 표에는 없는 코드</b>다 — 역할 기반 거부에 필요해 추가했고,
     * 노출 경로가 생기는 티켓에서 명세에 반영해야 한다.
     */
    FORBIDDEN(403, "FORBIDDEN", "권한이 없습니다.");

    private final int status;
    private final String code;
    private final String message;
}
