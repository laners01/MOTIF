package com.ssafy.motif.coreapi.global.docs;

import com.ssafy.motif.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SpecOrderCustomizer} — 문서에 나오는 순서가 <b>명세 절 순서</b>인지.
 *
 * <p>프로브 경로의 알파벳 순서를 <b>일부러 절 순서와 반대로</b> 깔았다. 커스터마이저가
 * 빠지면 springdoc 이 준 순서(경로·핸들러 등록 순)가 그대로 나와 이 테스트가 깨진다.
 *
 * <p>{@code §5.7} 과 {@code §5.10} 을 함께 두는 것도 계약이다 — 문자열로 비교하면
 * {@code "5.10" < "5.7"} 이라 §5.10 이 앞으로 나온다. 숫자로 비교해야 맞다.
 *
 * <p>{@link ResponseSchemaCustomizerTest} 와 같은 이유로 DB·Redis·MQ 없이 도는 문맥이다.
 */
@SpringBootTest(classes = SpecOrderCustomizerTest.DocsOnlyApp.class,
        properties = {
                "springdoc.enable-default-api-docs=false",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                        + "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration,"
                        + "org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration,"
                        + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                        + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration,"
                        + "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,"
                        + "org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration,"
                        + "org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration"})
@AutoConfigureMockMvc(addFilters = false)
class SpecOrderCustomizerTest {

    @Autowired
    private MockMvcTester mvc;

    @Test
    @DisplayName("경로는 태그 순서 → 절 번호 순으로 놓이고, 모르는 태그(어드민)는 뒤에 남는다")
    void ordersPathsBySpecSection() throws Exception {
        String doc = mvc.get().uri("/v3/api-docs/user").exchange().getResponse().getContentAsString();

        assertThat(indexOf(doc, "/api/v1/probe/zzz"))            // 인증 §2.1
                .isLessThan(indexOf(doc, "/api/v1/probe/aaa"));  // 인증 §2.2
        assertThat(indexOf(doc, "/api/v1/probe/aaa"))            // 인증 §2.2
                .isLessThan(indexOf(doc, "/api/v1/probe/nnn"));  // 아티스트 §5.7
        assertThat(indexOf(doc, "/api/v1/probe/nnn"))            // §5.7 이 §5.10 보다 앞이다
                .isLessThan(indexOf(doc, "/api/v1/probe/mmm"));  // 아티스트 §5.10
        assertThat(indexOf(doc, "/api/v1/probe/mmm"))
                .isLessThan(indexOf(doc, "/api/v1/probe/bbb"));  // 목록에 없는 태그
    }

    @Test
    @DisplayName("deprecated 는 절 번호가 앞서도 제 태그 맨 뒤다 — 목록 첫 줄이 쓰면 안 되는 API 면 안 된다")
    void sinksDeprecated() throws Exception {
        String doc = mvc.get().uri("/v3/api-docs/user").exchange().getResponse().getContentAsString();

        assertThat(indexOf(doc, "/api/v1/probe/mmm"))            // 아티스트 §5.10 — 살아 있다
                .isLessThan(indexOf(doc, "/api/v1/probe/ddd"));  // 아티스트 §5.1 — deprecated
    }

    @Test
    @DisplayName("태그 묶음도 명세 절 순서다 — 인증(§2)이 아티스트(§5)보다 앞")
    void ordersTags() throws Exception {
        String doc = mvc.get().uri("/v3/api-docs/user").exchange().getResponse().getContentAsString();

        assertThat(indexOf(doc, "\"name\":\"인증 (AUTH)\""))
                .isLessThan(indexOf(doc, "\"name\":\"아티스트 (ARTIST)\""));
    }

    /** 없으면 {@code -1} 이 조용히 "가장 앞" 이 되므로 찾지 못한 것을 실패로 만든다. */
    private int indexOf(String doc, String token) {
        int at = doc.indexOf(token);
        assertThat(at).as("문서에 %s 가 없다", token).isNotNegative();
        return at;
    }

    @Configuration
    @EnableAutoConfiguration
    static class DocsOnlyApp {

        @Bean
        GroupedOpenApi userApi() {
            return GroupedOpenApi.builder().group("user").pathsToMatch("/api/v1/**").build();
        }

        @Bean
        SpecOrderCustomizer specOrderCustomizer() {
            return new SpecOrderCustomizer();
        }

        @Bean
        AuthProbe authProbe() {
            return new AuthProbe();
        }

        @Bean
        ArtistProbe artistProbe() {
            return new ArtistProbe();
        }

        @Bean
        UnknownTagProbe unknownTagProbe() {
            return new UnknownTagProbe();
        }
    }

    @RestController
    @Tag(name = "인증 (AUTH)", description = "명세 §2")
    static class AuthProbe {

        @Operation(summary = "구글 로그인 (§2.1)")
        @GetMapping("/api/v1/probe/zzz")
        ApiResponse<Void> login() {
            return null;
        }

        @Operation(summary = "토큰 재발급 (§2.2)")
        @GetMapping("/api/v1/probe/aaa")
        ApiResponse<Void> refresh() {
            return null;
        }
    }

    @RestController
    @Tag(name = "아티스트 (ARTIST)", description = "명세 §5")
    static class ArtistProbe {

        @Operation(summary = "전체 아티스트 목록 (§5.10)")
        @GetMapping("/api/v1/probe/mmm")
        ApiResponse<Void> list() {
            return null;
        }

        @Operation(summary = "구독 (§5.7)")
        @GetMapping("/api/v1/probe/nnn")
        ApiResponse<Void> subscribe() {
            return null;
        }

        /** 전환 기간용 구 엔드포인트 — §1.7 미디어의 구 재생목록 경로가 이 자리다. */
        @Operation(summary = "구 랭킹 (§5.1)", deprecated = true)
        @GetMapping("/api/v1/probe/ddd")
        ApiResponse<Void> legacyRanking() {
            return null;
        }
    }

    /** 목록에 없는 태그 — 어드민 문서가 이 자리다. 절 번호가 있어도 앞으로 끌어오지 않는다. */
    @RestController
    @Tag(name = "어드민 프로브", description = "목록에 없는 태그")
    static class UnknownTagProbe {

        @Operation(summary = "소속사 목록 (§3.3)")
        @GetMapping("/api/v1/probe/bbb")
        ApiResponse<Void> companies() {
            return null;
        }
    }
}
