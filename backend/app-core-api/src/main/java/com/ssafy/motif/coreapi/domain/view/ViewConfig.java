package com.ssafy.motif.coreapi.domain.view;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 조회수 집계 설정 바인딩 + 대상별 버퍼 빈 (ADR-0015 · ADR-0016).
 *
 * <p>설정을 쓰는 패키지에서 직접 켠다({@code MediaConfig}·{@code RankingConfig} 와 같은
 * 규칙) — 애플리케이션 클래스에 모으면 무엇 때문에 켜졌는지가 파일 위치로 드러나지 않는다.
 *
 * <p><b>스케줄 등록은 {@link ViewScheduleConfig} 가 따로 갖는다.</b> 한 클래스에 두면
 * <b>순환 참조로 기동이 실패한다</b> — 이 클래스가 만드는 {@link ViewBuffer} 를
 * {@code ViewFlushJob} 이 받고, 스케줄 등록은 그 job 을 받아야 하므로
 * {@code ViewConfig → ViewFlushJob → ViewBuffer → ViewConfig} 가 된다.
 */
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(ViewProperties.class)
public class ViewConfig {

    private final ViewProperties properties;

    /**
     * 대상마다 버퍼를 하나씩 둔다.
     *
     * <p>{@link ViewBuffer} 에 {@code @Component} 를 붙이지 않고 여기서 만드는 것은
     * <b>같은 클래스의 인스턴스가 둘</b>이기 때문이다. 대상이 늘면 여기 한 줄을 더한다 —
     * 클래스를 복제하면 rotate·멱등 로직이 두 벌이 된다({@link ViewTarget} javadoc).
     */
    @Bean
    public ViewBuffer videoViewBuffer(StringRedisTemplate redis) {
        return new ViewBuffer(ViewTarget.VIDEO, redis, properties);
    }

    @Bean
    public ViewBuffer challengeViewBuffer(StringRedisTemplate redis) {
        return new ViewBuffer(ViewTarget.CHALLENGE, redis, properties);
    }
}
