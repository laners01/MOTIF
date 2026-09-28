package com.ssafy.motif.coreapi.domain.user;

import com.ssafy.motif.common.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 사용자 도메인 에러 코드 (API 명세 §4 · §15).
 *
 * <p>계정 상태(정지·삭제)로 <b>요청자</b>를 막는 코드는 여기 없다 —
 * {@code CommonErrorCode.ACCOUNT_SUSPENDED}·{@code ACCOUNT_DELETED} 가 §1.5 공통 분기로
 * 소유한다. 여기 있는 것은 <b>조회 대상</b>에 대한 응답이다.
 */
@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {

    /**
     * 조회 대상 사용자가 없다 (명세 §4.6).
     *
     * <p><b>삭제된 사용자도 이 코드다.</b> §1.8 공통 비노출이 "삭제 사용자는 존재를
     * 숨긴다"고 정했다 — 403 으로 갈라 내면 "그 사람은 있는데 탈퇴했다"가 응답에서
     * 읽힌다. 뷰어에서 작성자를 누르는 흐름이라 {@code userSeq} 를 찍어 볼 수 있는
     * 자리이기도 하다.
     */
    USER_NOT_FOUND(404, "USER_NOT_FOUND", "존재하지 않는 사용자입니다.");

    private final int status;
    private final String code;
    private final String message;
}
