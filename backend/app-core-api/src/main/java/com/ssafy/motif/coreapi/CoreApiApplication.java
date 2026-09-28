package com.ssafy.motif.coreapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 코어 API 실행체 — :8081 (관리 :9081).
 *
 * <p>엔티티·리포지토리는 {@code domain-core} 모듈에 있어 컴포넌트 스캔 기본 범위
 * ({@code com.ssafy.motif.coreapi}) 밖이다. 두 애너테이션으로 그 패키지를 명시한다.
 * {@code domain-notif}는 여기서 스캔하지 않는다 — app-notification의 것이며
 * 모듈 의존 자체가 금지다 (ADR-0004).
 *
 * <p>Boot 4 주의: {@code @EntityScan}이 {@code boot.autoconfigure.domain} →
 * {@code boot.persistence.autoconfigure}로 이동했다 (스타터 명칭 변경과 같은 계열 —
 * ADR-0004).
 */
@SpringBootApplication
@EntityScan(basePackages = "com.ssafy.motif.domain.core")
@EnableJpaRepositories(basePackages = "com.ssafy.motif.domain.core")
public class CoreApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoreApiApplication.class, args);
    }
}
