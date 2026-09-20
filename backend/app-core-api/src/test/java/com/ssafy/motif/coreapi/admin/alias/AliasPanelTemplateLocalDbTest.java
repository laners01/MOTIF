package com.ssafy.motif.coreapi.admin.alias;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 별칭 패널 프래그먼트가 두 화면에 실제로 끼워지는지 (어드민 §3.4.5~8 · §3.5.6~9) —
 * S15P21D104-358.
 *
 * <p><b>왜 렌더까지 보나.</b> {@code ~{admin/alias-panel :: aliasPanel}} 의 이름이 틀리면
 * 컴파일도 테스트도 통과하고 <b>화면을 열 때 500</b> 이 난다. 어드민 화면은 인증 뒤라
 * 브라우저로 확인하려면 운영 계정이 필요하고, 그 비밀번호는 팀 비밀 저장소에 있다 —
 * 그래서 템플릿 엔진을 직접 돌려 본다.
 *
 * <p>모델을 비워 둔다. 이 화면들은 Thymeleaf 가 뼈대만 그리고 데이터는 전부
 * {@code /api/v1/admin/**} fetch 다(§1.1) — 넘길 모델 속성이 없다.
 *
 * <p>{@code @SpringBootTest} 라 DB 가 필요해 {@code local-db} 태그를 단다. 템플릿 엔진만
 * 쓰지만 컨텍스트가 통째로 뜬다.
 */
@Tag("local-db")
@SpringBootTest
class AliasPanelTemplateLocalDbTest {

    @Autowired
    private SpringTemplateEngine templateEngine;

    @Test
    @DisplayName("아티스트 화면이 별칭 패널을 품고 렌더된다")
    void artistsPageIncludesPanel() {
        String html = templateEngine.process("admin/artists", new Context());

        assertThat(html)
                .contains("id=\"alias-panel\"")     // 프래그먼트가 실제로 끼워졌다
                .contains("id=\"alias-row\"")       // 행 template 까지 따라왔다
                .contains("/admin/aliases.js")      // 로직 스크립트가 실린다
                .contains("id=\"artists\"");        // 원래 내용도 그대로다
        // aliases.js 가 artists.js 보다 먼저다 — defer 는 문서 순서대로 실행된다
        assertThat(html.indexOf("/admin/aliases.js"))
                .isLessThan(html.indexOf("/admin/artists.js"));
    }

    @Test
    @DisplayName("소속사·그룹 화면이 별칭 패널과 별칭 버튼을 품고 렌더된다")
    void orgsPageIncludesPanel() {
        String html = templateEngine.process("admin/orgs", new Context());

        assertThat(html)
                .contains("id=\"alias-panel\"")
                .contains("id=\"alias-row\"")
                .contains("/admin/aliases.js")
                .contains("class=\"alias\">별칭")   // 그룹 행의 여는 버튼
                .contains("id=\"groups\"");
        assertThat(html.indexOf("/admin/aliases.js"))
                .isLessThan(html.indexOf("/admin/orgs.js"));
    }

    /**
     * 패널을 {@code layout.html} 이 아니라 별도 파일에 둔 이유의 회귀 방어.
     *
     * <p>layout 의 {@code page} 프래그먼트에 넣으면 <b>모든</b> 어드민 화면에 딸려 들어가고,
     * 끼워 쓰는 두 화면에서는 같은 id 가 두 번 나온다.
     */
    @Test
    @DisplayName("패널을 끼우지 않은 화면에는 별칭 마크업이 없다")
    void otherPagesHaveNoPanel() {
        assertThat(templateEngine.process("admin/songs", new Context()))
                .doesNotContain("alias-panel")
                .doesNotContain("alias-row");
    }
}
