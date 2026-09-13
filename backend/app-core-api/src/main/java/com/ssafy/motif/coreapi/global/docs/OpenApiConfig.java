package com.ssafy.motif.coreapi.global.docs;

import com.ssafy.motif.coreapi.admin.AdminPaths;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Swagger UI 구성. 경로는 springdoc 기본값을 유지한다
 * ({@code /swagger-ui.html}, {@code /v3/api-docs/{group}}).
 *
 * <p>문서는 <b>두 그룹</b>으로 갈린다 — 합본 기본 문서는
 * {@code springdoc.enable-default-api-docs=false} 로 꺼 두었다.
 * <ul>
 *   <li>{@code user} — 공인망 공개. {@code SecurityConfig} 가 permitAll 한다.</li>
 *   <li>{@code admin} — 테일넷 전용. 어드민 체인이 가져간다({@link AdminPaths#API_DOCS}) —
 *       공인망은 {@code AdminIpFilter} 가 404, 테일넷은 어드민 토큰으로 200.</li>
 * </ul>
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

    /**
     * 공인망에 나가는 문서. 어드민 경로를 뺀다 — 어드민 명세 §1.1 은 경로의 존재를 숨기려고
     * 403 이 아니라 404 를 쓰는데, 합본 문서가 그 경로를 전부 적어 두면 소용이 없다.
     */
    @Bean
    public GroupedOpenApi userApi() {
        return GroupedOpenApi.builder()
                .group("user")
                .pathsToMatch("/api/v1/**")
                .pathsToExclude(AdminPaths.API + "/**")
                .build();
    }

    /**
     * 어드민 문서. 테일넷 + 어드민 토큰으로만 열린다 (어드민 명세 §1.1).
     *
     * <p>경로를 {@link AdminPaths#API_DOCS} 로 고정해 두는 이유는 그 상수가
     * 어드민 체인·{@code AdminIpFilter} 와 같은 목록을 보기 때문이다. 여기 그룹 이름만
     * 바꾸면 문서 경로가 어긋나 사용자 체인으로 떨어지고, 어드민 토큰으로는 못 여는
     * 상태가 조용히 된다.
     */
    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
                .group("admin")
                .pathsToMatch(AdminPaths.API + "/**")
                .build();
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
                        모든 응답은 아래 봉투로 감싸져 온다(성공·실패 공통 — 명세 §1.2).
                        ```json
                        {
                          "result": "SUCCESS 또는 FAIL",
                          "code": "성공은 SUCCESS 고정, 실패는 에러코드 문자열",
                          "message": "성공은 \\"요청이 성공했습니다.\\" 고정, 실패는 사용자 표시용 메시지",
                          "data": { "...": "성공 payload, 실패는 null 또는 상세" },
                          "timestamp": "ISO-8601 UTC, 밀리초 3자리 (2026-09-10T00:00:00.000Z)"
                        }
                        ```
                        분기는 `result` 와 HTTP 상태로만 한다 — `message` 는 표시용이다.
                        검증 실패(400)일 때는 `data` 에 `[{"field": "...", "message": "..."}]` 형태의
                        필드별 오류 배열이 담긴다.

                        봉투를 반환하지 않는 예외가 하나 있다 — **좋아요 생성/취소 POST·DELETE 성공은
                        `204 No Content`** 이며 본문이 없다(명세 §1.2, §7.3·§8.3·§9.5·§10.2).

                        전 API 공통 실패는 400 `INVALID_PARAMETER`, 401 `UNAUTHORIZED`,
                        403 `ACCOUNT_SUSPENDED`/`ACCOUNT_DELETED`, 429 `RATE_LIMITED`,
                        500 `INTERNAL_ERROR` 다 (명세 §1.4). 각 API 문서에는 도메인 분기만 적는다.

                        ### 인증 방법
                        1. `POST /api/v1/auth/login/google` 에 구글 ID 토큰을 보내 `accessToken` 발급
                        2. 우측 상단 **Authorize** 버튼 → `accessToken` 값만 붙여넣기
                           (`Bearer ` 접두어는 Swagger UI 가 자동으로 붙인다)
                        3. 인증이 필요한 API 호출

                        `accessToken` 은 1시간 후 만료된다(응답의 `expiresIn` 이 초 단위 값이다).
                        만료되면 `POST /api/v1/auth/refresh` 로 재발급받는다 — refresh token 은 1회용이라
                        재발급 때마다 교체된다(§2.2). 로그아웃 `POST /api/v1/auth/logout`(§2.3)은
                        **아직 구현되지 않았다** — 별도 티켓이라 이 문서에도 없다.

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
