package com.ssafy.motif.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 알림 서비스 실행체 — :8082 (관리 :9082). evt.notification 소비 전용이다.
 *
 * <p>엔티티·리포지토리는 {@code domain-notif} 모듈에 있어 컴포넌트 스캔 기본 범위
 * ({@code com.ssafy.motif.notification}) 밖이다. 두 애너테이션으로 그 패키지를 명시한다.
 * {@code domain-core}는 여기서 스캔하지 않는다 — 스키마가 갈려 있고 모듈 의존
 * 자체가 금지다(ADR-0004). {@code motif_core.subscriptions} 는 읽기 전용 GRANT 로
 * {@code JdbcTemplate} 이 직접 읽는다({@code SubscriberTokenQuery}).
 *
 * <p><b>이 두 줄이 없으면 기동이 실패한다.</b> Spring Data 가 리포지토리를 0개 찾고
 * {@code FcmSender} 가 {@code DeviceTokenRepository} 를 주입받지 못한다 — 로그에는
 * "required a bean ... that could not be found" 만 남아 원인이 스캔 범위라는 것이
 * 바로 드러나지 않는다. CoreApiApplication 과 같은 구조다.
 *
 * <p><b>{@code @EnableConfigurationProperties} 는 여기 둔다.</b> {@code FcmConfig} 에
 * 두면 그 설정이 빠지는 {@code local} 프로파일에서 {@link NotificationProperties} 빈까지
 * 같이 사라져, 그것을 쓰는 {@code EventIdempotencyGuard} 가 주입에 실패한다 —
 * FCM 과 무관한 멱등 TTL 이 FCM 설정에 딸려 있을 이유가 없다.
 *
 * <p>Boot 4 주의: {@code @EntityScan} 이 {@code boot.autoconfigure.domain} →
 * {@code boot.persistence.autoconfigure} 로 이동했다.
 */
@SpringBootApplication
@EnableConfigurationProperties(com.ssafy.motif.notification.global.config.NotificationProperties.class)
@EntityScan(basePackages = "com.ssafy.motif.domain.notif")
@EnableJpaRepositories(basePackages = "com.ssafy.motif.domain.notif")
public class NotificationApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationApplication.class, args);
    }
}
