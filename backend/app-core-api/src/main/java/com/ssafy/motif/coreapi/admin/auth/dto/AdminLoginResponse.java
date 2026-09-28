package com.ssafy.motif.coreapi.admin.auth.dto;

/**
 * 로그인 성공 응답 (어드민 명세 §3.1.1).
 *
 * <p>{@code accessToken} 은 <b>API 도구용</b>이다. 화면은 이 값을 쓰지 않고 같은
 * 응답에 함께 실려 온 {@code motif_admin} 쿠키만 쓴다 — JS 가 토큰을 들고 있으면
 * {@code localStorage} 로 가고, HttpOnly 쿠키를 택한 이유가 사라진다(§1.2).
 *
 * @param expiresIn 초. {@code motif.admin.jwt.ttl}(12시간 = 43200) 에서 나온다 —
 *                  상수로 적지 않는다. 설정을 바꿨는데 응답만 옛 값을 말하면
 *                  화면의 만료 처리가 어긋난다
 */
public record AdminLoginResponse(
        int adminSeq, String name, int userSeq, String accessToken, long expiresIn
) {
}
