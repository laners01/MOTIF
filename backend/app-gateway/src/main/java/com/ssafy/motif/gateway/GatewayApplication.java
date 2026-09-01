package com.ssafy.motif.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 애플리케이션 게이트웨이 (ADR-0002 — Traefik은 "어디로", SCG는 "누가").
 * Phase 0은 라우팅과 내부 헤더 제거만 — JWT 필터·유저당 레이트리밋은 Phase 1.
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
