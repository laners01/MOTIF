package com.ssafy.motif.coreapi.domain.composite.worker;

import com.ssafy.motif.coreapi.domain.composite.CompositeProperties;
import com.ssafy.motif.coreapi.global.security.SecurityErrorWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 워커 콜백 전용 필터 체인 — 사용자 체인보다 <b>먼저</b> 매칭된다 (작업명세 §4.3).
 *
 * <p>{@code AdminSecurityConfig(@Order(1))} 와 같은 이유로 체인을 나눈다: 같은 체인에
 * 두면 요청 하나에 사용자 JWT 필터와 워커 토큰 필터가 모두 돌고, 무엇보다
 * {@code SecurityConfig} 가 catch-all {@code authenticated()} 라 <b>사용자 JWT 없이는
 * 401</b> 이다 — AI 는 우리 사용자가 아니다.
 *
 * <p><b>{@code SecurityConfig} 에 {@code permitAll} 을 더하지 않는다.</b> 그러면 인증이
 * 통째로 사라져 토큰 검사가 없는 경로가 되고, 거기에 다시 필터를 끼우면 규칙이 두
 * 군데로 갈린다. 경로별 체인이 이 레포의 방식이다.
 *
 * <p>순서는 어드민(1) → 워커(2) → catch-all(3) 이다. catch-all 은 경로 제한이 없어
 * 반드시 마지막이어야 한다 — 앞에 오면 나머지 체인이 영영 매칭되지 않는다.
 *
 * <p>세션을 만들지 않는다. 콜백은 한 번 쏘고 끝나는 요청이라 남길 상태가 없다.
 */
@Configuration
@RequiredArgsConstructor
public class WorkerSecurityConfig {

    /** {@code /api/worker/v1/…} — Ingress 의 {@code /api} Prefix 가 이미 덮는다. */
    public static final String PATTERN = "/api/worker/**";

    private final CompositeProperties properties;
    private final SecurityErrorWriter errorWriter;

    @Bean
    @Order(2)
    public SecurityFilterChain workerFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher(PATTERN)
                .csrf(csrf -> csrf.disable())
                .formLogin(login -> login.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 인가는 필터가 끝낸다 — 통과했으면 토큰이 맞은 것이고, 아니면 이미 401 이다
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(new WorkerTokenFilter(properties, errorWriter),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
