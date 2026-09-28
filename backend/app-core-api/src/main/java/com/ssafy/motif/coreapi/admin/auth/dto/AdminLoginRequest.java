package com.ssafy.motif.coreapi.admin.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 어드민 로그인 요청 (어드민 명세 §3.1.1).
 *
 * <p>길이 상한만 둔다. 형식 규칙(문자 종류·최소 길이)을 여기에 넣으면 <b>어떤 규칙을
 * 쓰는지가 미인증 응답으로 새어 나간다</b> — 계정 존재를 감추려고 401 을 하나로 합친
 * 설계와 어긋난다. 실제 판정은 해시 대조가 한다.
 *
 * @param loginId  {@code admins.login_id} VARCHAR(30)
 * @param password 평문. 로그에 남기지 않는다 — 이 record 는 {@code toString} 을
 *                 직접 막지 않지만, 예외·로그 어디에서도 요청 객체를 찍지 않는다
 */
public record AdminLoginRequest(
        @NotBlank @Size(max = 30) String loginId,
        @NotBlank @Size(max = 72) String password
) {

    /** 평문이 스택트레이스·로그로 새지 않도록 가린다. */
    @Override
    public String toString() {
        return "AdminLoginRequest[loginId=" + loginId + ", password=***]";
    }
}
