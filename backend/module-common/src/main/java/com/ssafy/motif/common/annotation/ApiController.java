package com.ssafy.motif.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 전역 예외 핸들러의 스코프를 제한하는 마커 (MODERA 관례 — ADR-0004).
 *
 * <p>{@code @RestControllerAdvice(annotations = ApiController.class)}가 이
 * 마커가 붙은 컨트롤러만 감싼다 — actuator·springdoc 등 우리 봉투를 쓰지 않는
 * 엔드포인트가 advice에 말려들지 않게 한다. {@code @RestController}와 함께 붙인다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiController {
}
