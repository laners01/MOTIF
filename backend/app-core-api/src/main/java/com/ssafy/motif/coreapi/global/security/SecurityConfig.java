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

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .formLogin(login -> login.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Traefik 계약 — 앱 포트의 얇은 health (README)
                        .requestMatchers(HttpMethod.GET, "/api/health").permitAll()
                        // 명세 §2 인증 API 중 🔓 표시된 것들. 로그아웃(§2.3)은 인증이 필요하다
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login/google").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/refresh").permitAll()
                        // 로컬 전용 발행 스모크 (ADR-0003, DevJobController는 @Profile("local"))
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
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
