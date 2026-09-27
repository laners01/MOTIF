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
        // 소속사 · 그룹 — 한 화면 두 탭 (§1.7 "아티스트" 줄의 3.3 · 3.4)
        registry.addViewController(AdminPaths.VIEW + "/orgs").setViewName("admin/orgs");
        // 에셋 가격 · 패스 가격 — 한 화면 두 탭 (§1.7 "모티프 에셋 활성/가격" · "패스 가격")
        registry.addViewController(AdminPaths.VIEW + "/prices").setViewName("admin/prices");
        // 공지 — 목록·수정 (§1.7, §3.10)
        registry.addViewController(AdminPaths.VIEW + "/notices").setViewName("admin/notice-list");
        // 작성 (§3.10.2) — API 는 402 로 있었는데 화면이 없어 등록할 길이 없었다.
        // /notices/{noticeSeq}/edit 보다 먼저 둘 필요는 없다 — 리터럴이 경로 변수보다 구체적이다
        registry.addViewController(AdminPaths.VIEW + "/notices/new").setViewName("admin/notice-new");
        registry.addViewController(AdminPaths.VIEW + "/notices/{noticeSeq}/edit").setViewName("admin/notice-edit");
        // 아티스트 — 목록·등록·수정 (§1.7, §3.5). 소속사·그룹 드롭다운은 §3.3.1·§3.4.1
        registry.addViewController(AdminPaths.VIEW + "/artists").setViewName("admin/artists");
        // 챌린지 목록 (§1.7, §3.7.1) — 등록은 위 /challenges/new 다
        registry.addViewController(AdminPaths.VIEW + "/challenges").setViewName("admin/challenges");
        // 모티프 에셋 목록 (§1.7, §3.8.1) — 활성·가격은 /prices 가 한다
        registry.addViewController(AdminPaths.VIEW + "/assets").setViewName("admin/assets");
        // 에셋 등록 (§3.8.2) — API 는 396·568 로 있었는데 화면이 없어 어드민 UI 로
        // 등록할 길이가 없었다. 리터럴이라 /assets 와 순서는 상관없다
        registry.addViewController(AdminPaths.VIEW + "/assets/new").setViewName("admin/asset-new");
        // 에셋 수정 (§3.8.3·§3.8.4) — 에셋만 등록 후 고칠 방법이 없었다.
        // assetSeq 는 화면 JS 가 location.pathname 에서 읽는다
        registry.addViewController(AdminPaths.VIEW + "/assets/{assetSeq}/edit").setViewName("admin/asset-edit");
        // 곡 — 목록·등록 (§1.7, §3.6). 수정은 API 가 없다(3.6.3 삭제 — 곡은 불변)
        registry.addViewController(AdminPaths.VIEW + "/songs").setViewName("admin/songs");
        // 챌린지 상세 (§3.7.3) — 읽기 전용. challengeSeq 는 화면 JS 가 pathname 에서 읽는다
        registry.addViewController(AdminPaths.VIEW + "/challenges/{challengeSeq}")
                .setViewName("admin/challenge-detail");
        // 숍 배너 — 목록·등록·순서·활성 한 화면 (§1.7, §3.11). 재정렬이 전량 교체라
        // 목록과 편집을 갈라 둘 이유가 없다
        registry.addViewController(AdminPaths.VIEW + "/banners").setViewName("admin/banners");
        // 신고 처리 — 목록·미리보기·인용/기각 한 화면 (§1.7, §3.13). 상세를 별도
        // 화면으로 빼지 않는다: 처리 버튼이 어느 신고의 것인지 헷갈리고, 목록으로
        // 돌아오는 길을 따로 만들어야 한다
        registry.addViewController(AdminPaths.VIEW + "/reports").setViewName("admin/reports");
        // 회원 — 검색·상세·정지/해제 한 화면 (§1.7, §3.14). 상세를 행으로 펼치는 이유는
        // 바로 위 신고와 같다. API(581·582)는 있었는데 화면이 없어 운영자가 브라우저로
        // 회원을 정지할 길이 없었다
        registry.addViewController(AdminPaths.VIEW + "/users").setViewName("admin/users");
        // 활동 대행 컨텍스트 선택 (ADMIN-11 · §1.7). 대행 API 는 409·574 로 있었는데
        // 아티스트를 고를 화면이 없어 어드민 UI 로는 대행할 길이 없었다
        registry.addViewController(AdminPaths.VIEW + "/act-as").setViewName("admin/act-as");
        // 참여 영상 탐색 + 댓글 대행 (ADMIN-11). 탐색 API(575)도 부르는 화면이 없었다
        registry.addViewController(AdminPaths.VIEW + "/act-as/videos").setViewName("admin/act-as-videos");
        // 공지 댓글 대행 (ADMIN-11). 스레드 로직은 영상 화면과 act-as-thread.js 를 공유한다
        registry.addViewController(AdminPaths.VIEW + "/act-as/notices").setViewName("admin/act-as-notices");
        // 챌린지 탐색 + 좋아요·댓글 대행 (ADMIN-11). 탐색 3종 중 마지막이다
        registry.addViewController(AdminPaths.VIEW + "/act-as/challenges").setViewName("admin/act-as-challenges");
        // 참여 영상 전체 목록·재생 (§3.12.1). 대행 화면은 아티스트 컨텍스트 안에서만 본다
        registry.addViewController(AdminPaths.VIEW + "/videos").setViewName("admin/videos");
        // 모티프 결과물 목록·재생 (§3.17.1)
        registry.addViewController(AdminPaths.VIEW + "/motif-results").setViewName("admin/motif-results");
    }
}
