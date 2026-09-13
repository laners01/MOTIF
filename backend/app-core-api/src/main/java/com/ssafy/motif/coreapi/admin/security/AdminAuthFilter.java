package com.ssafy.motif.coreapi.admin.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 어드민 JWT 검증 → SecurityContext (명세 §1.2) — 쿠키와 Bearer 둘 다 받는다.
 *
 * <p>화면(`/admin/**`)은 HttpOnly 쿠키 {@code motif_admin} 을, Postman·테스트는
 * {@code Authorization: Bearer} 를 쓴다. 쿠키가 {@code SameSite=Strict} 라 타 사이트의
 * 위조 요청에는 실리지 않는다 — 별도 CSRF 토큰을 두지 않는 근거다(§1.2).
 *
 * <p><b>스프링 빈이 아니다.</b> {@code Filter} 를 빈으로 두면 Boot 가 서블릿 컨테이너
 * 체인에도 자동 등록해, Security 체인 안에서는 {@code OncePerRequestFilter} 가 재실행을
 * 건너뛰고 빈 컨텍스트로 덮여 인증이 사라진다({@code JwtAuthenticationFilter} javadoc).
 * {@link AdminSecurityConfig} 가 직접 생성한다.
 *
 * <p>검증 실패는 여기서 응답하지 않고 <b>미인증으로 통과</b>시킨다 — 401 봉투는
 * 인가 필터 뒤의 entry point 가 한 곳에서 쓴다.
 */
@RequiredArgsConstructor
public class AdminAuthFilter extends OncePerRequestFilter {

    /** 화면용 세션 쿠키 이름 (§1.2). */
    public static final String COOKIE_NAME = "motif_admin";

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final AdminJwtProvider jwtProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null) {
            AuthenticatedAdmin admin = jwtProvider.parse(token);
            if (admin != null) {
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(admin, null,
                                List.of(new SimpleGrantedAuthority(ROLE_ADMIN))));
            } else {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    /** 쿠키 우선 — 화면이 쿠키만 쓰므로 브라우저 요청에서 헤더 탐색을 헛돌리지 않는다. */
    private String resolveToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (COOKIE_NAME.equals(cookie.getName()) && hasText(cookie.getValue())) {
                    return cookie.getValue();
                }
            }
        }
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            return token.isEmpty() ? null : token;
        }
        return null;
    }

    private static boolean hasText(String v) {
        return v != null && !v.isBlank();
    }
}
