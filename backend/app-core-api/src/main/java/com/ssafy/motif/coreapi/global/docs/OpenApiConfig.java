package com.ssafy.motif.coreapi.global.docs;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Swagger UI 구성. 경로는 springdoc 기본값을 유지한다
 * ({@code /swagger-ui.html}, {@code /v3/api-docs}) — {@code SecurityConfig} 가 permitAll 한다.
 *
 * <p>bearerAuth 스킴을 <b>전역</b>으로 요구한다. 인증이 필요 없는(🔓) 오퍼레이션은
 * 각 docs 인터페이스에서 {@code @SecurityRequirements} 로 그 요구를 해제한다 —
 * 전역만 걸고 끝내면 로그인 API 에도 잠금이 붙어 문서가 사실과 달라진다.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME_NAME = "Bearer Authentication";

    /** 운영 도메인. Jenkinsfile 의 HEALTH_URL 과 같은 호스트다. */
    @Value("${motif.docs.server-url:https://j15d104.p.ssafy.io}")
    private String prodServerUrl;

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(apiInfo())
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME_NAME))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME_NAME, bearerScheme()))
                .servers(servers());
    }

    private Info apiInfo() {
        return new Info()
                .title("MOTIF Core API")
                // 문서 원본과 버전을 맞춘다 — backend/docs/api/MOTIF_API_명세서_v0_11.md
                .version("v0.11")
                .description("""
                        `app-core-api` 의 공개 API(`/api/v1/**`) 명세다.
                        `app-notification` 은 HTTP 엔드포인트가 없는 이벤트 소비 전용이라 이 문서에 없다 (ADR-0009).

                        ### 응답 형태
                        모든 응답은 아래 봉투로 감싸져 온다(성공·실패 공통).
                        ```json
                        {
                          "result": "SUCCESS 또는 FAIL",
                          "code": "성공은 null, 실패는 에러코드 문자열",
                          "message": "사용자 표시용 메시지 (성공은 null)",
                          "data": { "...": "성공 payload, 실패는 보통 null" },
                          "timestamp": "ISO-8601 UTC"
                        }
                        ```
                        검증 실패(400)일 때는 `data` 에 `[{"field": "...", "message": "..."}]` 형태의
                        필드별 오류 배열이 담긴다.

                        전 API 공통 실패는 400 `INVALID_PARAMETER`, 401 `UNAUTHORIZED`,
                        403 `ACCOUNT_SUSPENDED`/`ACCOUNT_DELETED`, 429 `RATE_LIMITED`,
                        500 `INTERNAL_ERROR` 다 (명세 §1.4). 각 API 문서에는 도메인 분기만 적는다.

                        ### 인증 방법
                        1. `POST /api/v1/auth/login/google` 에 구글 ID 토큰을 보내 `accessToken` 발급
                        2. 우측 상단 **Authorize** 버튼 → `accessToken` 값만 붙여넣기
                           (`Bearer ` 접두어는 Swagger UI 가 자동으로 붙인다)
                        3. 인증이 필요한 API 호출

                        `accessToken` 은 1시간 후 만료된다(응답의 `expiresIn` 이 초 단위 값이다).
                        재발급 `POST /api/v1/auth/refresh` 와 로그아웃 `POST /api/v1/auth/logout` 은
                        명세 §2.2·§2.3 에 있으나 **아직 구현되지 않았다** — 별도 티켓이라 이 문서에도 없다.

                        `/api/health` 는 Traefik 과의 계약용 얇은 health 이고,
                        관리 엔드포인트(actuator·prometheus)는 관리 포트 9081 에만 열린다.
                        둘 다 클라이언트 API 가 아니라 이 문서에서 제외했다.
                        """);
    }

    private SecurityScheme bearerScheme() {
        // type=http 에서는 name 이 규격상 의미가 없다(apiKey 전용) — 설정하지 않는다.
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("POST /api/v1/auth/login/google 응답의 accessToken");
    }

    private List<Server> servers() {
        return List.of(
                // local·local-remote 모두 앱은 로컬 8081 에서 돈다 (ADR-0003)
                new Server().url("http://localhost:8081").description("로컬 실행 (./gradlew :app-core-api:bootRun)"),
                new Server().url(prodServerUrl).description("운영 — Traefik 인그레스")
        );
    }
}
