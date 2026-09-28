package com.ssafy.motif.coreapi.global.filter;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 클라이언트 IP 판정 — <b>한 곳에서만 정한다</b>.
 *
 * <p>{@code X-Forwarded-For} 의 <b>첫 값</b>이다. 위조가 통하지 않는 근거는 Traefik 이
 * 신뢰하지 않는 출처에서 온 이 헤더를 자기 값으로 덮어쓰기 때문이다(어드민 명세 §1.1).
 * 그 전제가 깨지면 아래 두 곳이 <b>동시에</b> 뚫린다:
 *
 * <ul>
 *   <li>{@code AdminIpFilter} — 어드민 경로 접근 제한(비허용 IP 는 404)</li>
 *   <li>{@code RequestIdFilter} — 액세스 로그의 출발지</li>
 * </ul>
 *
 * <p>판정이 두 군데로 갈리면 한쪽만 고쳤을 때 <b>로그에 찍힌 IP 와 실제로 막은 IP 가
 * 달라진다</b> — 사고 조사에서 가장 믿고 싶은 값이 거짓말을 하게 된다. 그래서 묶는다.
 *
 * <p>2026-09-15 실측: 공인망에서 {@code X-Forwarded-For: 100.64.0.1} 을 위조해도
 * {@code /admin} 이 404 였다. Traefik 기본 동작이 막고 있으나 <b>명시 설정이 아니라
 * 기본값에 기대는 상태</b>다(인프라 부록 C의 {@code forwardedHeaders.trustedIPs} 미설정).
 */
public final class ClientIp {

    private static final String HEADER_FORWARDED_FOR = "X-Forwarded-For";

    private ClientIp() {
    }

    /** 앞단이 없으면(로컬·테스트) {@code remoteAddr} 로 떨어진다. */
    public static String of(HttpServletRequest request) {
        String forwarded = request.getHeader(HEADER_FORWARDED_FOR);
        if (forwarded == null || forwarded.isBlank()) {
            return request.getRemoteAddr();
        }
        int comma = forwarded.indexOf(',');
        return (comma < 0 ? forwarded : forwarded.substring(0, comma)).trim();
    }
}
