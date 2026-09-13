package com.ssafy.motif.coreapi.admin.view;

import com.ssafy.motif.coreapi.admin.AdminPaths;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 어드민 화면 라우팅 (어드민 명세 §1.7 화면 목록).
 *
 * <p><b>@Controller 를 만들지 않는다.</b> 화면은 Thymeleaf 로 뼈대만 그리고 데이터는
 * 전부 {@code /api/v1/admin/**} 을 fetch 한다(§1.1 — 경로 2종). 서버가 모델에 넣어 줄
 * 값이 없으므로 경로 → 뷰 이름 매핑이 전부다.
 *
 * <p>경로 변수({@code noticeSeq})도 모델로 넘기지 않는다 — 화면 JS 가
 * {@code location.pathname} 에서 읽어 상세를 조회한다. 서버에서 한 번 더 꺼내 봐야
 * 같은 값을 두 경로로 전달하게 될 뿐이다.
 *
 * <p>정적 자원은 {@code static/admin/} 에 둔다 — {@code /admin/**} 이라 어드민 체인의
 * IP·인증 제한(§1.1·§1.2)을 그대로 받는다. 버킷을 따로 파면 JS 가 인증 없이 노출돼
 * 경로 존재 자체를 감추는 404 규약이 깨진다.
 */
@Configuration
public class AdminViewConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // 로그인 — 유일하게 인증 없이 열리는 화면 (§3.1.1, AdminSecurityConfig).
        // 스타일·스크립트를 인라인으로 들고 있다. static/admin/ 이 인증 뒤에 있어
        // layout.html 을 끼우면 로그인 전에는 CSS·JS 가 401 이기 때문이다(login.html 주석).
        registry.addViewController(AdminPaths.VIEW + "/login").setViewName("admin/login");
        // 챌린지 등록 — 원본 업로드 → 슬롯 입력 → 편집본 키 매트릭스 (§1.7, 부록 A)
        registry.addViewController(AdminPaths.VIEW + "/challenges/new").setViewName("admin/challenge-new");
        // 에셋 가격 · 패스 가격 — 한 화면 두 탭 (§1.7 "모티프 에셋 활성/가격" · "패스 가격")
        registry.addViewController(AdminPaths.VIEW + "/prices").setViewName("admin/prices");
        // 공지 — 목록·수정 (§1.7, §3.10)
        registry.addViewController(AdminPaths.VIEW + "/notices").setViewName("admin/notice-list");
        registry.addViewController(AdminPaths.VIEW + "/notices/{noticeSeq}/edit").setViewName("admin/notice-edit");
        // 아티스트 — 목록·등록·수정 (§1.7, §3.5). 소속사·그룹 드롭다운은 §3.3.1·§3.4.1
        registry.addViewController(AdminPaths.VIEW + "/artists").setViewName("admin/artists");
        // 챌린지 목록 (§1.7, §3.7.1) — 등록은 위 /challenges/new 다
        registry.addViewController(AdminPaths.VIEW + "/challenges").setViewName("admin/challenges");
        // 모티프 에셋 목록 (§1.7, §3.8.1) — 활성·가격은 /prices 가 한다
        registry.addViewController(AdminPaths.VIEW + "/assets").setViewName("admin/assets");
        // 곡 — 목록·등록 (§1.7, §3.6). 수정은 API 가 없다(3.6.3 삭제 — 곡은 불변)
        registry.addViewController(AdminPaths.VIEW + "/songs").setViewName("admin/songs");
    }
}
