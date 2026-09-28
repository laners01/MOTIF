package com.ssafy.motif.coreapi.admin.security;

import com.ssafy.motif.coreapi.admin.AdminPaths;
import com.ssafy.motif.coreapi.global.security.RestAccessDeniedHandler;
import com.ssafy.motif.coreapi.global.security.RestAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 어드민 전용 필터 체인 (명세 §1.1·§1.2) — 사용자 체인보다 <b>먼저</b> 매칭된다.
 *
 * <p>체인을 나눈 이유: 같은 체인에 두면 요청 하나에 사용자 JWT 필터와 어드민 JWT 필터가
 * 모두 돌고, 먼저 채운 인증을 뒤가 덮을 수 있다. 경로로 갈라 두면 어드민 요청에는
 * 어드민 토큰만, 사용자 요청에는 사용자 토큰만 유효하다 — 교차 사용 차단(§1.2)이
 * 키 분리·{@code typ} 클레임에 더해 구조로도 보장된다.
 *
 * <p>IP 제한은 여기가 아니라 {@link AdminIpFilter}(서블릿 컨테이너 단계)다 — 404 를
 * 내려면 Security 의 401/403 경로보다 바깥이어야 한다.
 *
 * <p>CSRF 토큰은 두지 않는다 — 쿠키가 {@code SameSite=Strict} 이고 접근이 테일넷
 * 한정이다(§1.2 기본안).
 */
@Configuration
@EnableConfigurationProperties(AdminProperties.class)
@RequiredArgsConstructor
public class AdminSecurityConfig {

    private final AdminJwtProvider adminJwtProvider;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    @Bean
    @Order(1)
    public SecurityFilterChain adminFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher(AdminPaths.PATTERNS)
                .csrf(csrf -> csrf.disable())
                .formLogin(login -> login.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 명세 3.1.1 🔓 로그인. 화면 로그인 페이지도 토큰 없이 열려야 한다
                        .requestMatchers(HttpMethod.POST, AdminPaths.API + "/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, AdminPaths.VIEW + "/login").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                // 빈이 아니라 여기서 생성한다 (AdminAuthFilter javadoc — 이중 등록 시 인증 유실)
                .addFilterBefore(new AdminAuthFilter(adminJwtProvider),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * 어드민 비밀번호 대조용 (§3.1.1). 시드 해시는 부록 B 가 {@code htpasswd -bnBC 10}
     * 또는 이 인코더로 만든다 — 두 경로의 cost(10)가 같아야 같은 해시가 나온다.
     *
     * <p>팬 앱에는 비밀번호가 없다(구글 OAuth 뿐) — 그래서 전역 설정이 아니라
     * 유일한 사용처인 어드민 쪽에 둔다. 팬 앱에 비밀번호 로그인이 생기면 그때
     * 공용 위치로 옮긴다.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}
