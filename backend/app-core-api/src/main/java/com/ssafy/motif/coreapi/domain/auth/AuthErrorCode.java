package com.ssafy.motif.coreapi.domain.auth;

import com.ssafy.motif.common.error.CommonErrorCode;
import com.ssafy.motif.common.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 인증 도메인 에러 코드 (API 명세 §2·§15).
 *
 * <p>공통 코드는 여기 두지 않는다 — {@code UNAUTHORIZED}·{@code FORBIDDEN}·
 * {@code ACCOUNT_SUSPENDED}·{@code ACCOUNT_DELETED} 는 명세 §1.4 가 전 API 공통으로
 * 규정하므로 {@link CommonErrorCode} 가 소유한다(code 전역 유일 규칙).
 * 이 enum 은 인증 도메인에서만 나오는 분기만 갖는다.
 */
@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

    /** 구글 ID 토큰 검증 실패 — 만료·위조·aud 불일치 (명세 §2.1). */
    INVALID_GOOGLE_TOKEN(401, "INVALID_GOOGLE_TOKEN", "구글 인증에 실패했습니다. 다시 로그인해 주세요.");

    private final int status;
    private final String code;
    private final String message;
}
