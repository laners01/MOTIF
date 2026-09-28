package com.ssafy.motif.coreapi.admin.auth;

import com.ssafy.motif.coreapi.admin.security.AdminAuthFilter;
import com.ssafy.motif.coreapi.admin.security.AdminProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code motif_admin} 쿠키 속성 (어드민 명세 §1.2).
 *
 * <p>쿠키 플래그는 틀려도 로그인이 되므로 수동 확인으로는 잘 안 걸린다 —
 * {@code SameSite} 가 빠지면 CSRF 토큰을 두지 않은 근거가 사라지고, {@code HttpOnly}
 * 가 빠지면 JS 가 토큰을 읽을 수 있게 되는데 둘 다 화면은 멀쩡히 돈다.
 */
class AdminSessionCookieTest {

    private static final Duration TTL = Duration.ofHours(12);

    private AdminSessionCookie cookie(boolean secure) {
        return new AdminSessionCookie(new AdminProperties(
                List.of(), new AdminProperties.Jwt("irrelevant-for-cookie", TTL), secure));
    }

    @Test
    @DisplayName("발급 — HttpOnly · Secure · SameSite=Strict · Path=/ · 만료는 토큰 TTL 과 같다")
    void 발급() {
        ResponseCookie issued = cookie(true).issue("token-value");

        assertThat(issued.getName()).isEqualTo(AdminAuthFilter.COOKIE_NAME);
        assertThat(issued.getValue()).isEqualTo("token-value");
        assertThat(issued.isHttpOnly()).isTrue();
        assertThat(issued.isSecure()).isTrue();
        assertThat(issued.getSameSite()).isEqualTo("Strict");
        assertThat(issued.getPath()).isEqualTo("/");
        assertThat(issued.getMaxAge()).as("쿠키가 토큰보다 오래 남으면 안 된다").isEqualTo(TTL);
    }

    @Test
    @DisplayName("로컬(http)에서는 Secure 를 끈다 — 켜면 브라우저가 쿠키를 저장하지 않는다")
    void 로컬은_secure_끔() {
        assertThat(cookie(false).issue("t").isSecure()).isFalse();
    }

    @Test
    @DisplayName("삭제 — 이름·Path 가 발급과 같아야 브라우저가 같은 쿠키로 보고 덮어쓴다")
    void 삭제() {
        ResponseCookie issued = cookie(true).issue("token-value");
        ResponseCookie cleared = cookie(true).clear();

        assertThat(cleared.getValue()).isEmpty();
        assertThat(cleared.getMaxAge()).isZero();
        assertThat(cleared.getName()).isEqualTo(issued.getName());
        assertThat(cleared.getPath()).isEqualTo(issued.getPath());
    }
}
