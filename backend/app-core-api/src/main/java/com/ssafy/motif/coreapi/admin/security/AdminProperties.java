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
 */
@ConfigurationProperties(prefix = "motif.admin")
public record AdminProperties(List<String> allowedCidrs, Jwt jwt) {

    /**
     * @param ttl 만료. 명세 §1.2 는 12시간·refresh 없음(기본안).
     */
    public record Jwt(String secret, Duration ttl) {
    }
}
