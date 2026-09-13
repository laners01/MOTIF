package com.ssafy.motif.coreapi.admin;

/**
 * 어드민 경로 2종 (어드민 명세 §1.1) — <b>한 곳에서만 정한다</b>.
 *
 * <p>IP 필터·인증 필터·Security 체인이 같은 목록을 봐야 한다. 한쪽만 바뀌면
 * 화면은 막히는데 JSON API 는 열린 채로 남는 식의 구멍이 생긴다.
 */
public final class AdminPaths {

    /** Thymeleaf 화면. */
    public static final String VIEW = "/admin";

    /** JSON API (envelope). */
    public static final String API = "/api/v1/admin";

    /**
     * 어드민 OpenAPI 문서 ({@code GroupedOpenApi} 그룹 {@code admin}).
     *
     * <p>화면·API 와 같은 취급을 받아야 한다. 사용자 체인에 두면 <b>사용자</b> JWT 를
     * 요구해서 어드민 로그인으로는 영영 열리지 않고, 그렇다고 공개하면 어드민 경로
     * 19개를 그대로 흘린다 — 여기 넣어야 공인망 404·테일넷 어드민 토큰 200 이 된다.
     */
    public static final String API_DOCS = "/v3/api-docs/admin";

    /** Security 체인 매칭용 패턴. */
    public static final String[] PATTERNS = {VIEW + "/**", API + "/**", API_DOCS};

    private AdminPaths() {
    }

    /** 요청 URI 가 어드민 경로인가 — 필터가 서블릿 컨테이너 단계에서 쓴다. */
    public static boolean matches(String uri) {
        return uri.startsWith(VIEW + "/") || uri.equals(VIEW)
                || uri.startsWith(API + "/") || uri.equals(API)
                || uri.equals(API_DOCS);
    }
}
