package com.ssafy.motif.coreapi.global.security;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
 * <p><b>{@code /admin}의 팀 IP·테일넷 제한은 여기가 아니다</b> — Traefik
 * {@code ipAllowList} 미들웨어가 맡는다(ADR-0009 D3). 앱에서 하면 프록시 뒤
 * 클라이언트 IP를 헤더로 신뢰해야 한다.
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
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                // CorsConfigurationSource 빈이 있을 때만 실제로 동작한다. 그 빈은
                // LocalCorsConfig 가 @Profile("local") 로만 제공하므로 운영에는 CORS 가 없다.
                // preflight(OPTIONS)는 인가 필터보다 앞에서 처리돼야 해서 여기 선언이 필요하다.
                .cors(org.springframework.security.config.Customizer.withDefaults())
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
                        // 로컬 전용 도구 (ADR-0003). 컨트롤러 자체가 @Profile("local") 이라
                        // 다른 프로파일에서는 이 경로에 매핑이 없어 404 다 — 이 permitAll 은
                        // 경로를 여는 것이 아니라 로컬에서 인증을 면제하는 것이다
                        .requestMatchers("/api/dev/**").permitAll()
                        // springdoc — 관리 포트가 아니라 앱 포트에 뜬다(기본 경로 유지)
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
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
