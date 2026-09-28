package com.ssafy.motif.coreapi.domain.composite;

import com.ssafy.motif.coreapi.domain.composite.service.CompositeJobDispatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * 디스패처 주기 등록 (작업명세 §4.7 — S15P21D104-537).
 *
 * <p><b>{@code registrar.setTaskScheduler()} 를 부르지 않는다.</b> 여러
 * {@link SchedulingConfigurer} 가 <b>같은</b> {@link ScheduledTaskRegistrar} 를 받으므로
 * 두 곳에서 스케줄러를 심으면 나중에 도는 쪽이 앞의 것을 조용히 덮는다 — 풀 설정이
 * 한쪽만 적용되고 예외도 경고도 없다. 스케줄러는 {@code RankingConfig} 가 소유한다
 * ({@code VideoRankingConfig} 와 같은 판단).
 *
 * <p><b>{@code @Scheduled} 문자열 크론을 쓰지 않는다.</b> {@code fixedDelayString} 은
 * ISO-8601({@code PT1M})이나 밀리초 정수만 받아서, 설정에 팀 표기({@code 1m})를 넣으면
 * 기동 시점에 터진다. {@link java.time.Duration} 을 그대로 넘기면 문자열 파싱이 없다.
 *
 * <p>{@code fixedDelay} 다 — 앞 틱이 끝난 뒤부터 센다. S3 HEAD·접수 HTTP 가 섞여 있어
 * 한 틱이 주기를 넘길 수 있는데, {@code fixedRate} 면 그때 같은 잡을 두 틱이 함께 집는다.
 *
 * <p>{@code @EnableScheduling} 을 여기도 붙인다. {@code RankingConfig} 가 이미 켰지만
 * 거기에 기대면 랭킹이 걷히는 날 <b>이 배치가 조용히 멈춘다</b> — 그러면 접수 실패가
 * 영영 복구되지 않는다.
 */
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class CompositeDispatcherConfig implements SchedulingConfigurer {

    private final CompositeProperties properties;
    private final CompositeJobDispatcher dispatcher;

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addFixedDelayTask(dispatcher::sweep, properties.sweepInterval());
    }
}
