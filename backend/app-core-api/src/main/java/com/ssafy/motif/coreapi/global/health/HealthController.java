package com.ssafy.motif.coreapi.global.health;

import com.ssafy.motif.common.annotation.ApiController;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 앱 포트의 얇은 health (확정 결정 — Traefik이 앱 포트만 보므로).
 * 계약: {@code GET /api/health} → 200 {@code {"status":"UP"}} — 봉투 없이 그대로.
 * actuator health·prometheus는 관리 포트(9081)에만 노출한다.
 *
 * <p>{@code @Hidden} — 이건 Traefik 과의 계약이고 클라이언트 API 가 아니다. 문서에
 * 남기면 전역 bearerAuth 요구를 상속해 잠금 표시가 붙는데, 실제로는 permitAll 이라
 * 오해를 준다. 계약 자체는 README·ADR 에 기록돼 있다.
 */
@Hidden
@ApiController
@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
