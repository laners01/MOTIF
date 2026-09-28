package com.ssafy.motif.coreapi.global.security;

import com.ssafy.motif.coreapi.domain.auth.jwt.JwtProvider;
import com.ssafy.motif.domain.core.user.UserRole;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * {@code Authorization: Bearer <access token>} 검증 → SecurityContext 채움
 * (ADR-0009 D2 — 게이트웨이가 하던 일).
 *
 * <p><b>스프링 빈이 아니다.</b> {@code Filter} 를 빈으로 두면 Boot 가 서블릿 컨테이너
 * 체인에도 자동 등록해 필터가 두 번 놓인다. 그러면 컨테이너 체인(Security 앞)에서 먼저
 * 실행돼 인증을 채우고, 뒤이어 Security 의 {@code SecurityContextHolderFilter} 가 비어 있는
 * 컨텍스트로 덮어쓰며, {@code OncePerRequestFilter} 라 체인 안에서는 재실행이 건너뛰어져
 * <b>인증이 사라진다</b>(유효한 토큰에도 401). {@code SecurityConfig} 가 직접 생성한다.
 *
 * <p>검증 실패는 여기서 응답하지 않고 <b>인증하지 않은 채 통과</b>시킨다.
 * 보호된 경로면 뒤의 인가 필터가 걸러 {@link RestAuthenticationEntryPoint}가
 * 401 봉투를 쓰고, permitAll 경로면 그대로 처리된다 — 판정을 한 곳에 모은다.
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String CLAIM_ROLE = "role";
    private static final String AUTHORITY_PREFIX = "ROLE_";

    private final JwtProvider jwtProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null) {
            authenticate(token);
        }
        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(header) || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    private void authenticate(String token) {
        try {
            Claims claims = jwtProvider.parse(token);
            UserRole role = UserRole.valueOf(claims.get(CLAIM_ROLE, String.class));
            AuthenticatedUser principal =
                    new AuthenticatedUser(Integer.valueOf(claims.getSubject()), role);

            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, null,
                            List.of(new SimpleGrantedAuthority(AUTHORITY_PREFIX + role.name()))));
        } catch (RuntimeException e) {
            // 서명·만료 실패, claims 형식 오류 모두 "인증 안 됨"으로 동일하게 취급한다.
            log.debug("access token 인증 실패 — 미인증으로 진행: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        }
    }
}
