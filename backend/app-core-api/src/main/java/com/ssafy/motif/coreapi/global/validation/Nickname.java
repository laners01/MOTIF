package com.ssafy.motif.coreapi.global.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 닉네임 형식 제약 — <b>한 곳에서만 정한다</b> (API 명세 §3.3 · §4.2).
 *
 * <p>온보딩 확정(§3.3)과 마이페이지 수정(§4.2)이 <b>같은 규칙</b>을 써야 한다. 각자
 * {@code @Pattern} 을 들고 있으면 한쪽만 고쳤을 때 "온보딩은 통과하는데 수정은 400" 같은
 * 상태가 되고, 그 어긋남은 사용자가 이미 쓰고 있는 닉네임을 못 바꾸는 모양으로 나타난다.
 *
 * <p><b>상수가 아니라 애너테이션인 이유.</b> 패턴만 공유하면 {@code @NotNull} 짝이 여전히
 * 갈라진다. 한쪽이 그것을 빠뜨리면 {@code {"nickname": null}} 이 검증을 통과해 엔티티까지
 * 내려가고, DB CHECK(1~50자) 위반으로 <b>400 이어야 할 것이 500 이 된다</b>. 제약 전체를
 * 묶어야 그 틈이 없다.
 *
 * <p><b>{@code @NotBlank} 가 아니라 {@code @NotNull} 이다.</b> {@code @NotBlank} 로 두면
 * 빈 문자열이 {@code @NotBlank}·{@code @Pattern}(최소 2자) 양쪽에 걸려 같은 필드에 오류가
 * <b>2건</b> 나간다. 명세 §4.2 의 실패 예시는 1건이고, {@code @NotNull} 이면 {@code null}
 * 은 필수 오류로 {@code ""}·{@code " "} 는 형식 오류로 갈려 <b>어떤 입력에서도 정확히
 * 1건</b>이 된다.
 *
 * <p><b>숫자를 허용한다.</b> 명세 §3.3 의 열거("영문·한글·{@code .}·{@code _}")에는 없지만
 * §2.1 이 가입 시 발급하는 임시 닉네임이 {@code user_{seq}}(예 {@code user_1}) 라, 숫자를
 * 막으면 <b>서버가 만든 값이 서버의 검증을 통과하지 못하는</b> 모순이 생긴다
 * (2026-09-12 팀 확정 — 원본 명세서에도 반영이 필요하다).
 *
 * <p>중복 검사는 하지 않는다 — {@code users.nickname} 에 UNIQUE 가 없다(C1 회의 확정).
 */
@NotNull(message = Nickname.MESSAGE_REQUIRED)
@Pattern(regexp = Nickname.PATTERN, message = Nickname.MESSAGE_FORMAT)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER,
        ElementType.RECORD_COMPONENT, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = {})
@Documented
public @interface Nickname {

    /** 영문·한글·숫자·{@code .}·{@code _} 2~20자. */
    String PATTERN = "^[a-zA-Z0-9가-힣._]{2,20}$";

    String MESSAGE_FORMAT = "2~20자의 영문·한글·숫자·마침표·언더바만 허용합니다.";

    String MESSAGE_REQUIRED = "nickname은 필수입니다";

    String message() default MESSAGE_FORMAT;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
