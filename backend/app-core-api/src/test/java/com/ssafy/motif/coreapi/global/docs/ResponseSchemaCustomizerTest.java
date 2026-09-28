package com.ssafy.motif.coreapi.global.docs;

import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.common.response.CursorResponse;
import com.ssafy.motif.coreapi.domain.challenge.dto.ChallengeFeedItem;
import com.ssafy.motif.coreapi.domain.user.dto.UserMeResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ResponseSchemaCustomizer} — 성공 응답에 스키마가 붙고 DTO 가 {@code components} 에
 * 등록되는지. <b>Swagger 하단 Schemas 가 비면 안드로이드가 필드를 볼 곳이 없다.</b>
 *
 * <p>실제 컨트롤러 대신 <b>프로브 컨트롤러</b>를 띄운다. DB·Redis·MQ 없이 도는 문맥이라
 * Jenkins 의 기본 {@code test} 에서 그대로 돌고, 무엇보다 <b>고장 모양을 직접 재현한다</b> —
 * 프로브의 {@code @Content} 에는 {@code examples} 만 있고 {@code schema} 가 없다. 그게
 * docs 인터페이스 40여 곳의 모양이고, 커스터마이저가 없으면 여기서 스키마가 비어 나온다.
 *
 * <p>{@code GroupedOpenApi} 를 하나 두는 것도 계약의 일부다 — 그룹이 있으면 평범한
 * {@code OpenApiCustomizer} 는 붙지 않는다({@link ResponseSchemaCustomizer} javadoc).
 */
@SpringBootTest(classes = ResponseSchemaCustomizerTest.DocsOnlyApp.class,
        properties = {
                "springdoc.enable-default-api-docs=false",
                // 문서 생성에 DB·Redis·MQ 는 필요 없다. 빼야 기본 test 에서 돈다
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                        + "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration,"
                        + "org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration,"
                        + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                        + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration,"
                        + "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,"
                        + "org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration,"
                        + "org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration"})
@AutoConfigureMockMvc(addFilters = false)   // 이 문맥에는 앱의 SecurityConfig 가 없다
class ResponseSchemaCustomizerTest {

    @Autowired
    private MockMvcTester mvc;

    @Test
    @DisplayName("예시만 있는 성공 응답에도 스키마가 붙고, 중첩 DTO 까지 components 에 실린다")
    void fillsSuccessSchema() throws Exception {
        String doc = mvc.get().uri("/v3/api-docs/user").exchange().getResponse().getContentAsString();

        assertThat(doc)
                // 봉투 — 반환 타입 그대로 풀린다
                .contains("#/components/schemas/ApiResponseUserMeResponse")
                .contains("#/components/schemas/ApiResponseCursorResponseChallengeFeedItem")
                // 항목 DTO 와 그 안에 중첩된 DTO 까지
                .contains("\"UserMeResponse\"")
                .contains("\"VideoPreviewItem\"")
                .contains("\"ChallengeFeedItem\"")
                // @Schema(description=...) 이 설명으로 실려 나온다
                .contains("마이페이지 원콜 (§4.1)");
    }

    @Configuration
    @EnableAutoConfiguration
    static class DocsOnlyApp {

        @Bean
        GroupedOpenApi userApi() {
            return GroupedOpenApi.builder().group("user").pathsToMatch("/api/v1/**").build();
        }

        @Bean
        ResponseSchemaCustomizer responseSchemaCustomizer(RequestMappingHandlerMapping handlerMapping) {
            return new ResponseSchemaCustomizer(handlerMapping);
        }

        @Bean
        Probe probe() {
            return new Probe();
        }
    }

    /** docs 인터페이스와 같은 모양 — {@code @Content} 에 예시만 있고 {@code schema} 가 없다. */
    @RestController
    static class Probe {

        @GetMapping("/api/v1/probe/me")
        @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "단건",
                content = @Content(mediaType = "application/json",
                        examples = @ExampleObject(value = "{\"result\":\"SUCCESS\"}"))))
        ApiResponse<UserMeResponse> me() {
            return null;
        }

        @GetMapping("/api/v1/probe/feed")
        @ApiResponses(@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "커서 목록",
                content = @Content(mediaType = "application/json",
                        examples = @ExampleObject(value = "{\"result\":\"SUCCESS\"}"))))
        ApiResponse<CursorResponse<ChallengeFeedItem>> feed() {
            return null;
        }
    }
}
