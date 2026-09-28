package com.ssafy.motif.common.error;

/**
 * 도메인별 에러 코드 enum이 구현하는 계약 (응답·예외 3종 — ADR-0004).
 *
 * <p><b>code 문자열은 전역 유일하다.</b> 모듈·도메인이 달라도 중복될 수 없다 —
 * 클라이언트·로그·문서가 code 하나로 원인을 특정하기 위한 규칙이다.
 *
 * <p><b>형식은 enum 상수와 같은 이름 문자열</b>이다({@code INVALID_PARAMETER},
 * {@code VIDEO_NOT_FOUND} 등). API 명세 §1.4·§15 가 이 형태를 클라이언트 계약으로
 * 적고 있고 §15 는 "module-common 의 에러 enum 은 이 표와 1:1 로 구현"한다고
 * 못박는다 — 안드로이드가 이 값으로 분기하므로 숫자 코드({@code C001} 계열)를
 * 쓰지 않는다. 신규 도메인 enum 은 §15 표에 있는 코드를 그대로 쓰고, 표에 없는
 * 코드가 필요하면 명세에 먼저 추가한다.
 *
 * <p>공통 코드는 {@link CommonErrorCode} 가 소유한다 — 도메인 enum 에 같은 코드를
 * 다시 만들지 않는다.
 */
public interface ErrorCode {

    /** HTTP 상태 코드(예: 400, 500). spring-web 의존을 피하기 위해 int로 둔다. */
    int getStatus();

    String getCode();

    String getMessage();
}
