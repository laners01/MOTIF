package com.ssafy.motif.coreapi.admin.view;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * 어드민 화면이 공통 레이아웃으로 렌더되는지 (어드민 명세 §1.7).
 *
 * <p>프래그먼트 파라미터 문법({@code ~{admin/layout :: page(…, ~{::main})}})이 틀리면
 * 기동 시점이 아니라 <b>그 화면을 열 때</b> 500 이 난다 — 화면마다 한 번씩 열어 보는
 * 대신 여기서 한 번에 건다. 스프링 컨텍스트는 띄우지 않는다(엔진만 필요하다).
 */
class AdminTemplateRenderTest {

    private static final SpringTemplateEngine ENGINE = engine();

    /** Boot 가 쓰는 엔진과 같은 구성 — 표현식은 OGNL 이 아니라 SpEL 이다. */
    private static SpringTemplateEngine engine() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    private String render(String view) {
        return ENGINE.process(view, new Context(Locale.KOREAN));
    }

    @Test
    @DisplayName("챌린지 등록 — 레이아웃 제목과 매트릭스 표 뼈대가 들어간다")
    void challengeNew() {
        String html = render("admin/challenge-new");

        assertThat(html)
                .contains("<title>MOTIF 어드민 · 챌린지 등록</title>")
                .contains("/admin/admin.js")
                .contains("/admin/challenge-new.js")
                .contains("id=\"matrix\"")
                .contains("id=\"slot-row\"");
        // 레이아웃이 끼워지지 않고 th:* 가 그대로 남는 실패를 잡는다
        assertThat(html).doesNotContain("th:replace").doesNotContain("th:fragment");
    }
}
