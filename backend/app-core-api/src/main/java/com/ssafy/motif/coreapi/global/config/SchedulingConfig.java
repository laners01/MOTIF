package com.ssafy.motif.coreapi.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * 스케줄러 <b>소유자</b> — 풀을 만들고 등록기에 심는 곳은 레포에 여기 하나뿐이다.
 *
 * <p>예전에는 §5.1 {@code RankingConfig} 가 겸했다. 그 클래스가 랭킹 주기를 잃으면서
 * (ADR-0016 — 랭킹이 조회수 집계 배치의 단계가 됐다) 풀만 남게 돼 여기로 옮겼다.
 * 그쪽 javadoc 이 *"이 클래스를 걷어낼 때 풀 설정이 함께 사라지지 않게 주의한다"* 고
 * 적어 둔 것이 실제로 일어난 셈이다.
 *
 * <p><b>{@code setTaskScheduler()} 를 여기서만 부른다.</b> 여러
 * {@link SchedulingConfigurer} 가 <b>같은</b> {@link ScheduledTaskRegistrar} 를 받으므로
 * 두 곳에서 스케줄러를 심으면 나중에 도는 쪽이 앞의 것을 조용히 덮는다 — 풀 설정이
 * 한쪽만 적용되고 예외도 경고도 없다. 작업을 더하는 쪽
 * ({@code ViewScheduleConfig}·{@code CompositeDispatcherConfig})은 {@code addFixedDelayTask}
 * 만 부른다.
 *
 * <p><b>풀이 2다.</b> 현재 등록된 작업이 둘이다 — 조회수 집계(20분, 랭킹 둘을 포함)와
 * 합성 잡 스윕(1분). 기본값은 1스레드라 둘이 겹치는 틱에서 한쪽이 밀리는데, 그 경우
 * <b>예외도 경고도 없이 주기만 어긋나</b> 원인을 찾기 어렵다. 작업을 더할 때 이 값도
 * 함께 올린다.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig implements SchedulingConfigurer {

    @Bean(destroyMethod = "shutdown")
    public ThreadPoolTaskScheduler motifTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("motif-sched-");
        // 종료 시 돌던 배치를 끊지 않는다 — 조회수 집계가 RENAMENX 직후에 끊기면 그
        // 구간이 processing 에 남고, 되살리는 것은 다음 기동의 재처리 몫이 된다.
        // 깔끔하게 끝낼 수 있으면 그 편이 낫다
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(20);
        scheduler.initialize();
        return scheduler;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.setTaskScheduler(motifTaskScheduler());
    }
}
