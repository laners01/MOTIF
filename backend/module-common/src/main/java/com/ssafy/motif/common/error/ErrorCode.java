package com.ssafy.motif.common.error;

/**
 * 도메인별 에러 코드 enum이 구현하는 계약 (응답·예외 3종 — ADR-0004).
 *
 * <p><b>code 문자열은 전역 유일하다.</b> 모듈·도메인이 달라도 중복될 수 없다 —
 * 클라이언트·로그·문서가 code 하나로 원인을 특정하기 위한 규칙이다.
 * 형식은 영역 접두사 + 3자리 숫자(공통은 {@code C___}), 신규 도메인 enum은
 * 자기 접두사를 예약하고 리뷰에서 기존 코드와의 충돌을 확인한다.
 */
public interface ErrorCode {

    /** HTTP 상태 코드(예: 400, 500). spring-web 의존을 피하기 위해 int로 둔다. */
    int getStatus();

    String getCode();

    String getMessage();
}
