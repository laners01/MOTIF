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

    /** Security 체인 매칭용 패턴. */
    public static final String[] PATTERNS = {VIEW + "/**", API + "/**"};

    private AdminPaths() {
    }

    /** 요청 URI 가 어드민 경로인가 — 필터가 서블릿 컨테이너 단계에서 쓴다. */
    public static boolean matches(String uri) {
        return uri.startsWith(VIEW + "/") || uri.equals(VIEW)
                || uri.startsWith(API + "/") || uri.equals(API);
    }
}
