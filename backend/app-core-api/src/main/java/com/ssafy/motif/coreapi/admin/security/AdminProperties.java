package com.ssafy.motif.coreapi.admin.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * 어드민 공통 설정 (어드민 명세 §1.1·§1.2).
 *
 * @param allowedCidrs 접근 허용 대역 — 테일넷(100.64.0.0/10) + 팀 고정 IP.
 *                     비허용은 404 다({@link AdminIpFilter}). 재배포 없이 바꾸도록
 *                     configmap 값으로 둔다(§1.1).
 * @param jwt          어드민 JWT — {@code secret} 은 팬 앱 토큰과 <b>반드시 다른 키</b>다.
 *                     키가 같으면 {@code typ} 클레임 하나가 교차 사용을 막는 유일한
 *                     방어선이 된다(§1.2).
 * @param cookieSecure {@code motif_admin} 쿠키의 {@code Secure} 플래그 (§1.2).
 *                     기본값은 {@code application.yml} 의 {@code true} 이고 로컬
 *                     프로파일만 {@code false} 로 덮는다 — record 의 boolean 은 값이
 *                     없으면 {@code false} 로 바인딩되므로 <b>기본을 켠 쪽에 둬야</b>
 *                     빠뜨린 프로파일이 평문 전송으로 새지 않는다. 로컬만 끄는 이유는
 *                     개발 서버가 http 이고 브라우저가 Secure 쿠키를 저장하지 않기
 *                     때문이다. 요청의 스킴으로 판단하지 않는 이유: TLS 를 Traefik 이
 *                     끊어서 앱이 보는 스킴은 운영에서도 http 다
 */
@ConfigurationProperties(prefix = "motif.admin")
public record AdminProperties(List<String> allowedCidrs, Jwt jwt, boolean cookieSecure) {

    /**
     * @param ttl 만료. 명세 §1.2 는 12시간·refresh 없음(기본안).
     */
    public record Jwt(String secret, Duration ttl) {
    }
}
