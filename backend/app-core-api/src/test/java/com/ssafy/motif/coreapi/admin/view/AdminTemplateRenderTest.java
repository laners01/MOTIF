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
    @DisplayName("로그인 — 인증이 걸린 정적 자원을 부르지 않고 혼자 선다 (§3.1.1 permitAll)")
    void login() {
        String html = render("admin/login");

        assertThat(html)
                .contains("<title>MOTIF 어드민 · 로그인</title>")
                .contains("id=\"loginId\"")
                .contains("id=\"password\"")
                .contains("/api/v1/admin/auth/login");
        // 핵심 회귀 방어 — layout.html 을 끼우면 /admin/admin.css·admin.js 를 부르는데
        // 그 둘은 인증 뒤에 있어 로그인 전에는 401 이다. 스타일이 깨진 화면이 뜬다.
        assertThat(html)
                .as("로그인 화면은 스타일·스크립트를 인라인으로 들고 있어야 한다")
                .doesNotContain("/admin/admin.css")
                .doesNotContain("/admin/admin.js")
                .contains("<style>");
        assertThat(html).doesNotContain("th:replace").doesNotContain("th:fragment");
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

    @Test
    @DisplayName("소속사 · 그룹 — 두 탭과 행 뼈대가 한 화면에 들어간다")
    void orgs() {
        String html = render("admin/orgs");

        assertThat(html)
                .contains("<title>MOTIF 어드민 · 소속사 · 그룹</title>")
                .contains("/admin/orgs.js")
                .contains("data-tab=\"company\"")
                .contains("data-tab=\"group\"")
                .contains("id=\"company-row\"")
                .contains("id=\"group-row\"");
        assertThat(html).doesNotContain("th:replace").doesNotContain("th:fragment");
    }

    @Test
    @DisplayName("가격 관리 — 에셋·패스 두 탭이 한 화면에 들어간다")
    void prices() {
        String html = render("admin/prices");

        assertThat(html)
                .contains("<title>MOTIF 어드민 · 가격 관리</title>")
                .contains("/admin/prices.js")
                .contains("data-tab=\"asset\"")
                .contains("data-tab=\"pass\"")
                .contains("id=\"pass-grid\"");
        assertThat(html).doesNotContain("th:replace").doesNotContain("th:fragment");
    }

    @Test
    @DisplayName("공지 목록 — 행 뼈대와 삭제분 필터가 들어간다")
    void noticeList() {
        String html = render("admin/notice-list");

        assertThat(html)
                .contains("<title>MOTIF 어드민 · 공지 목록</title>")
                .contains("/admin/notice-list.js")
                .contains("id=\"include-deleted\"")
                .contains("id=\"notice-row\"");
        assertThat(html).doesNotContain("th:replace").doesNotContain("th:fragment");
    }

    @Test
    @DisplayName("공지 수정 — 이미지 전체 교체 경고와 드래그 목록이 들어간다")
    void noticeEdit() {
        String html = render("admin/notice-edit");

        assertThat(html)
                .contains("<title>MOTIF 어드민 · 공지 수정</title>")
                .contains("/admin/notice-edit.js")
                .contains("통째로 교체")
                .contains("draggable=\"true\"");
        assertThat(html).doesNotContain("th:replace").doesNotContain("th:fragment");
    }

    @Test
    @DisplayName("아티스트 관리 — 목록 행 뼈대와 소속사 잠금 안내가 들어간다 (§3.5.2)")
    void artists() {
        String html = render("admin/artists");

        assertThat(html)
                .contains("<title>MOTIF 어드민 · 아티스트 관리</title>")
                .contains("/admin/artists.js")
                .contains("id=\"artist-row\"")
                .contains("id=\"company-lock\"")
                .contains("id=\"group\"");
        assertThat(html).doesNotContain("th:replace").doesNotContain("th:fragment");
    }

    @Test
    @DisplayName("챌린지 목록 — 행 뼈대와 등록 화면 링크가 들어간다 (§3.7.1)")
    void challenges() {
        String html = render("admin/challenges");

        assertThat(html)
                .contains("<title>MOTIF 어드민 · 챌린지 목록</title>")
                .contains("/admin/challenges.js")
                .contains("id=\"challenge-row\"")
                .contains("/admin/challenges/new");
        assertThat(html).doesNotContain("th:replace").doesNotContain("th:fragment");
    }
}
