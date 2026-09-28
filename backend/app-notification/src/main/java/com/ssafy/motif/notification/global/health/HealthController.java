package com.ssafy.motif.notification.global.health;

import com.ssafy.motif.common.annotation.ApiController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 앱 포트의 얇은 health (확정 결정 — 전 모듈 통일).
 * 계약: {@code GET /api/health} → 200 {@code {"status":"UP"}}.
 * actuator health·prometheus는 관리 포트(9082)에만 노출한다.
 */
@ApiController
@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
