package com.ssafy.motif.coreapi.admin.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 어드민 접근 2단 방어 — IP 제한(404)과 토큰 검증 (어드민 명세 §1.1·§1.2).
 *
 * <p>필터를 직접 돌린다. 두 필터가 서로 다른 체인(서블릿 컨테이너 / Security)에 있어
 * MockMvc 한 판으로 함께 검증할 수 없고, 각자의 판정 규칙만 보면 충분하다.
 */
class AdminAccessFilterTest {

    private static final String ADMIN_SECRET = "test-ADMIN-secret-key-at-least-32-bytes-long!!";
    private static final String USER_SECRET = "test-USER--secret-key-at-least-32-bytes-long!!";
    private static final String ADMIN_API = "/api/v1/admin/media/presign";

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("AdminIpFilter — 비허용은 404 (경로 존재 비노출)")
    class Ip {

        private final AdminIpFilter filter = ipFilter(List.of("100.64.0.0/10", "127.0.0.1/32"));

        @Test
        @DisplayName("테일넷 밖에서 오면 403 이 아니라 404 다")
        void 비허용_404() throws Exception {
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(request(ADMIN_API, "203.0.113.9"), response, chain);

            assertThat(response.getStatus()).isEqualTo(404);
            assertThat(chain.getRequest()).as("체인을 타지 않아야 한다").isNull();
        }

        @Test
        @DisplayName("허용 대역이면 통과한다")
        void 허용_통과() throws Exception {
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(request(ADMIN_API, "100.64.1.5"), new MockHttpServletResponse(), chain);

            assertThat(chain.getRequest()).isNotNull();
        }

        @Test
        @DisplayName("X-Forwarded-For 가 여러 개면 첫 값(진짜 클라이언트)으로 판정한다")
        void 프록시_체인() throws Exception {
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request(ADMIN_API, "203.0.113.9, 100.64.0.1"), response, new MockFilterChain());

            assertThat(response.getStatus()).isEqualTo(404);
        }

        @Test
        @DisplayName("로그인 화면도 막는다 — permitAll 은 Security 안쪽 얘기고 IP 필터가 더 바깥이다")
        void 로그인_화면도_404() throws Exception {
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(request("/admin/login", "203.0.113.9"), response, chain);

            assertThat(response.getStatus()).isEqualTo(404);
            assertThat(chain.getRequest()).as("체인을 타지 않아야 한다").isNull();
        }

        @Test
        @DisplayName("어드민 경로가 아니면 손대지 않는다")
        void 사용자_경로() throws Exception {
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(request("/api/v1/onboarding", "203.0.113.9"),
                    new MockHttpServletResponse(), chain);

            assertThat(chain.getRequest()).isNotNull();
        }

        @Test
        @DisplayName("허용 목록이 비면 전부 막는다 — 설정 누락이 조용히 열리면 안 된다")
        void 설정_누락은_fail_closed() throws Exception {
            MockHttpServletResponse response = new MockHttpServletResponse();

            ipFilter(List.of()).doFilter(request(ADMIN_API, "100.64.1.5"), response, new MockFilterChain());

            assertThat(response.getStatus()).isEqualTo(404);
        }

        private AdminIpFilter ipFilter(List<String> cidrs) {
            return new AdminIpFilter(new AdminProperties(cidrs, null, true));
        }

        private MockHttpServletRequest request(String uri, String forwardedFor) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
            request.addHeader("X-Forwarded-For", forwardedFor);
            return request;
        }
    }

    @Nested
    @DisplayName("AdminAuthFilter — 쿠키·Bearer 둘 다, typ=ADMIN 만")
    class Auth {

        private final AdminJwtProvider provider = new AdminJwtProvider(
                new AdminProperties(List.of(), new AdminProperties.Jwt(ADMIN_SECRET, Duration.ofHours(12)), true));
        private final AdminAuthFilter filter = new AdminAuthFilter(provider);
        private final AuthenticatedAdmin admin = new AuthenticatedAdmin(1, 2, "김운영");

        @Test
        @DisplayName("화면용 HttpOnly 쿠키로 인증된다")
        void 쿠키() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", ADMIN_API);
            request.setCookies(new Cookie(AdminAuthFilter.COOKIE_NAME, provider.issue(admin)));

            filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

            assertThat(principal()).isEqualTo(admin);
        }

        @Test
        @DisplayName("API 도구용 Bearer 로도 인증된다")
        void 베어러() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", ADMIN_API);
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + provider.issue(admin));

            filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

            assertThat(principal()).isEqualTo(admin);
        }

        @Test
        @DisplayName("팬 앱 토큰은 서명키가 달라 통과하지 못한다 — 교차 사용 차단 1선")
        void 키_분리() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", ADMIN_API);
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + fanToken(USER_SECRET));

            filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        @DisplayName("키가 같아져도 typ 이 ADMIN 이 아니면 거부한다 — 2선")
        void typ_검증() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", ADMIN_API);
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + fanToken(ADMIN_SECRET));

            filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        @DisplayName("만료된 토큰은 통과하지 못한다")
        void 만료() throws Exception {
            AdminJwtProvider expired = new AdminJwtProvider(new AdminProperties(
                    List.of(), new AdminProperties.Jwt(ADMIN_SECRET, Duration.ofSeconds(-1)), true));
            MockHttpServletRequest request = new MockHttpServletRequest("GET", ADMIN_API);
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + expired.issue(admin));

            filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        /** 사용자 access token 모양 — {@code typ} 클레임이 없다. */
        private String fanToken(String secret) {
            return Jwts.builder()
                    .subject("7")
                    .claim("role", "USER")
                    .expiration(new Date(System.currentTimeMillis() + 60_000))
                    .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                    .compact();
        }

        private Object principal() {
            return SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        }
    }
}
