package com.ssafy.motif.coreapi.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * 로컬 개발 도구용 CORS (격리 모드 — ADR-0003).
 *
 * <p><b>운영에는 CORS 가 필요 없다.</b> 클라이언트는 안드로이드 네이티브 앱이라 브라우저
 * 동일 출처 정책의 대상이 아니고, {@code /admin} 은 core-api 가 같은 출처로 서빙한다.
 * 그래서 이 빈은 {@code @Profile("local")} 로 묶어 다른 프로파일에서는 아예 생성되지 않는다 —
 * 빈이 없으면 {@code SecurityConfig} 의 {@code cors()} 도 적용할 설정이 없어 헤더를 내지 않는다.
 *
 * <p>필요한 이유는 하나뿐이다: {@code local-infra/google-login-test.html} 이
 * {@code localhost:5500} 에서 서빙되는데 API 는 {@code localhost:8081} 이라 교차 출처다.
 */
@Configuration
@Profile("local")
public class LocalCorsConfig {

    /** 구글 로그인 테스트 페이지의 정적 서버(README 의 python http.server 5500). */
    private static final List<String> LOCAL_TOOL_ORIGINS =
            List.of("http://localhost:5500", "http://127.0.0.1:5500");

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(LOCAL_TOOL_ORIGINS);
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-Id"));
        config.setExposedHeaders(List.of("X-Request-Id"));
        // 쿠키를 쓰지 않는다(토큰은 Authorization 헤더) — 자격증명 허용은 열지 않는다
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
