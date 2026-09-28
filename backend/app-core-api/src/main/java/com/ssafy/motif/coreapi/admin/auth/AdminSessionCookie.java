package com.ssafy.motif.coreapi.admin.auth;

import com.ssafy.motif.coreapi.admin.security.AdminAuthFilter;
import com.ssafy.motif.coreapi.admin.security.AdminProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 화면용 세션 쿠키 {@code motif_admin} 조립 (어드민 명세 §1.2).
 *
 * <p>발급과 삭제가 <b>같은 속성</b>을 써야 한다 — 브라우저는 name·Path·Domain 이
 * 모두 일치해야 같은 쿠키로 보고 덮어쓴다. 두 곳에 따로 적으면 로그아웃이 조용히
 * 실패하고(쿠키가 남는다) 화면은 로그인된 것처럼 보인다. 그래서 한 곳에 모은다.
 *
 * <p>쿠키 이름은 {@link AdminAuthFilter#COOKIE_NAME} 을 그대로 쓴다 — 읽는 쪽과
 * 쓰는 쪽이 문자열을 각자 들고 있으면 한쪽만 고쳐도 컴파일이 통과한다.
 *
 * <p>{@code SameSite=Strict} 라 타 사이트에서 온 요청에는 쿠키가 실리지 않는다.
 * CSRF 토큰을 두지 않는 근거가 이것이다(§1.2, 테일넷 한정 접근 전제).
 */
@Component
public class AdminSessionCookie {

    private static final String SAME_SITE_STRICT = "Strict";

    private final boolean secure;
    private final Duration ttl;

    AdminSessionCookie(AdminProperties properties) {
        this.secure = properties.cookieSecure();
        this.ttl = properties.jwt().ttl();
    }

    /** 로그인 성공 — 토큰 만료와 쿠키 만료를 같은 값으로 맞춘다. */
    public ResponseCookie issue(String token) {
        return base(token).maxAge(ttl).build();
    }

    /**
     * 로그아웃 — 값을 비우고 즉시 만료시킨다 (§3.1.2).
     *
     * <p>서버에는 지울 것이 없다. 폐기 목록을 두지 않기로 했으므로(§1.2 stateless)
     * 이미 발급된 토큰은 만료까지 유효하다 — Bearer 로 들고 있던 도구는 계속 쓸 수
     * 있고, 그게 이 설계의 알려진 한계다.
     */
    public ResponseCookie clear() {
        return base("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(AdminAuthFilter.COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(SAME_SITE_STRICT)
                .path("/");
    }
}
