package com.ssafy.motif.coreapi.global.docs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * api-docs 가 공인망에 어드민 경로를 흘리지 않는지 (어드민 명세 §1.1).
 *
 * <p>운영 {@code /v3/api-docs} 가 어드민 경로 19개를 그대로 노출하던 것을 막은 장치라,
 * 계약을 <b>실제 HTTP 응답</b>으로 고정한다. GroupedOpenApi 빈의 필드를 단언하면
 * {@code enable-default-api-docs} 를 되켜거나 permitAll 을 되돌려도 통과한다.
 *
 * <p>DB 가 필요한 테스트는 아니지만 전체 컨텍스트를 띄우므로 {@code local-db} 태그에 둔다 —
 * 기본 {@code test} 는 DB·MQ·Redis 가 없는 에이전트에서 돈다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiGroupLocalDbTest {

    @Autowired
    private MockMvcTester mvc;

    @Test
    @DisplayName("합본 기본 문서는 나오지 않는다 — 그게 어드민 19경로를 흘리던 곳이다")
    void defaultApiDocsIsGone() {
        // 매핑이 사라져 401(Security 가 없는 핸들러보다 먼저 답한다)이지만, 계약은
        // "문서가 나오지 않는다" 다 — 401/404 중 무엇인지에 테스트를 묶지 않는다
        assertThat(mvc.get().uri("/v3/api-docs")).hasStatus4xxClientError();
    }

    @Test
    @DisplayName("user 그룹은 공개이고 어드민 경로가 한 줄도 없다")
    void userGroupHasNoAdminPath() {
        assertThat(mvc.get().uri("/v3/api-docs/user"))
                .hasStatusOk()
                .bodyText()
                .contains("/api/v1/auth/login/google")
                .doesNotContain("/api/v1/admin");
    }

    @Test
    @DisplayName("admin 그룹 문서는 공인망에서 열리지 않는다 (인그레스 앞에 한 겹 더)")
    void adminGroupIsNotPublic() {
        assertThat(mvc.get().uri("/v3/api-docs/admin")).hasStatus(401);
    }
}
