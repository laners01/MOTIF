package com.ssafy.motif.coreapi.global.security;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.ssafy.motif.coreapi.domain.auth.jwt.AuthProperties;
import com.ssafy.motif.coreapi.domain.auth.jwt.JwtProvider;

/**
 * 인증·인가 필터 체인 (ADR-0009 D2 — SCG 제거로 core-api가 소유).
 *
 * <p>세션을 만들지 않는다(JWT). CSRF는 쿠키·폼을 쓰지 않으므로 끈다.
 *
 * <p><b>어드민 경로는 이 체인이 아니다</b> —
 * {@code admin.security.AdminSecurityConfig}(@Order(1))와
 * {@code domain.composite.worker.WorkerSecurityConfig}(@Order(2))가 먼저 매칭한다. 팀 IP·테일넷
 * 제한도 Traefik 이 아니라 {@code AdminIpFilter} 가 맡는다: 비허용 응답이 <b>404</b>여야
 * 하는데 Traefik {@code ipAllowList} 는 403 만 낼 수 있다(어드민 명세 §1.1, 회의 확정 R1
 * — ADR-0009 D3 의 "Traefik 담당" 서술을 대체한다).
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(AuthProperties.class)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtProvider jwtProvider;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    @Bean
    @Order(3)   // 어드민(1)·워커 콜백(2) 다음 — 이 체인은 경로 제한이 없는 catch-all 이라 마지막이어야 한다
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .formLogin(login -> login.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 404·500 이 나면 서블릿이 /error 로 재디스패치한다. 이 경로가 막혀 있으면
                        // ERROR 디스패치에서 JWT 필터가 돌지 않아(OncePerRequestFilter 기본값) 익명이 되고,
                        // 진짜 상태 코드가 401 로 덮인다 — 경로 오타에 "토큰이 잘못됐다"는 응답이 가는 셈이다
                        .requestMatchers("/error").permitAll()
                        // Traefik 계약 — 앱 포트의 얇은 health (README)
                        .requestMatchers(HttpMethod.GET, "/api/health").permitAll()
                        // 명세 §2 인증 API 중 🔓 표시된 것들. 로그아웃(§2.3)은 인증이 필요하다
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login/google").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/refresh").permitAll()
                        // 개발 도구 경로. DevAuthController·DevJobController 는 @Profile("local")
                        // 이라 운영에는 매핑이 없다(404). 다만 login-test.html 은 정적 리소스라
                        // 프로파일과 무관하게 운영에서도 열린다 — 의도된 것이다(배포 서버에서
                        // 구글 로그인을 확인하는 유일한 수단). 페이지 자체는 권한을 주지 않고
                        // 이미 공개인 /api/v1/auth/login/google 만 호출한다.
                        .requestMatchers("/api/dev/**").permitAll()
                        // springdoc — 관리 포트가 아니라 앱 포트에 뜬다(기본 경로 유지).
                        // 공개는 user 그룹 문서까지다. /v3/api-docs/admin 은 이 체인에 없다 —
                        // AdminPaths.API_DOCS 로 어드민 체인이 가져간다(공인망 404, 어드민 토큰 200)
                        .requestMatchers("/v3/api-docs/swagger-config", "/v3/api-docs/user",
                                "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // 관리 포트(9081)는 별도 컨텍스트라 이 체인이 닿지 않는 것이 정상이지만,
                        // 같은 컨텍스트로 합쳐지는 구성 변경에도 probe·스크랩이 죽지 않게 열어둔다
                        .requestMatchers("/actuator/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                // 빈이 아니라 여기서 생성한다 — 빈이면 서블릿 컨테이너 체인에도 자동 등록돼
                // 인증이 유실된다 (JwtAuthenticationFilter javadoc 참조)
                .addFilterBefore(new JwtAuthenticationFilter(jwtProvider),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
