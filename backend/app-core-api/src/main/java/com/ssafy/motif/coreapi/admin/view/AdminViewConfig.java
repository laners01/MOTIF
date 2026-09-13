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
        // 챌린지 등록 — 원본 업로드 → 슬롯 입력 → 편집본 키 매트릭스 (§1.7, 부록 A)
        registry.addViewController(AdminPaths.VIEW + "/challenges/new").setViewName("admin/challenge-new");
        // 에셋 가격 · 패스 가격 — 한 화면 두 탭 (§1.7 "모티프 에셋 활성/가격" · "패스 가격")
        registry.addViewController(AdminPaths.VIEW + "/prices").setViewName("admin/prices");
    }
}
