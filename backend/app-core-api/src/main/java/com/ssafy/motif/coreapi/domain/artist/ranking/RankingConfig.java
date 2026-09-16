package com.ssafy.motif.coreapi.domain.artist.ranking;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * 랭킹 설정 바인딩 + <b>레포 최초의 {@code @EnableScheduling}</b>.
 *
 * <p>스케줄링을 애플리케이션 클래스가 아니라 여기서 켜는 것은, 전역 스케줄링이
 * <b>랭킹 때문에</b> 켜졌다는 사실이 파일 위치로 드러나게 하기 위해서다
 * ({@code MediaConfig} 와 같은 규칙 — 설정을 쓰는 패키지에서 직접 켠다).
 *
 * <p><b>{@code @Scheduled} 애너테이션을 쓰지 않는다.</b> {@code fixedDelayString} 은
 * ISO-8601(<code>PT5M</code>)이나 밀리초 정수만 받아서, 설정에 팀이 쓰는 표기(<code>5m</code>)를
 * 넣으면 <b>기동 시점에 터진다.</b> 같은 yml 안에서 {@code half-life: 14d} 는 되고
 * {@code interval: 5m} 만 안 되는 규칙을 남기느니, 여기서 {@code Duration} 을 그대로
 * 넘겨 등록한다 — 문자열 파싱이 아예 없어진다.
 *
 * <p><b>스케줄러 풀이 3이다.</b> 기본값은 1스레드라 {@code :current}(5분)와
 * {@code :prev}(10분)가 겹치는 틱에서 한쪽이 다른 쪽이 끝날 때까지 밀린다. 지금 규모로는
 * 밀려도 티가 안 나지만, 그 경우 <b>예외도 경고도 없이 주기만 어긋나</b> 원인을 찾기 어렵다.
 * 세 번째 자리는 §6.1 HOT 랭킹 배치 몫이다 — {@code VideoRankingConfig} 가 같은
 * {@code ScheduledTaskRegistrar} 에 작업을 더하고 스케줄러는 여기 것을 쓴다(그쪽
 * javadoc: 스케줄러를 두 곳에서 심으면 한쪽이 조용히 덮인다). <b>이 클래스를 걷어낼
 * 때 풀 설정이 함께 사라지지 않게 주의한다.</b>
 */
@Configuration
@EnableScheduling
@RequiredArgsConstructor
@EnableConfigurationProperties(RankingProperties.class)
public class RankingConfig implements SchedulingConfigurer {

    private final RankingProperties properties;
    private final ArtistRankingSnapshotJob job;

    @Bean(destroyMethod = "shutdown")
    public ThreadPoolTaskScheduler rankingTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(3);
        scheduler.setThreadNamePrefix("ranking-sched-");
        // 종료 시 돌던 배치를 끊지 않는다 — 전량 재계산이라 중간에 끊겨도 데이터가
        // 깨지진 않지만, RENAME 직전에 끊기면 :building 키가 남는다(다음 실행이 지운다)
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(20);
        scheduler.initialize();
        return scheduler;
    }

    /**
     * {@code fixedRate} 가 아니라 {@code fixedDelay} 다 — 앞 실행이 끝난 뒤부터 주기를
     * 센다. {@code fixedRate} 로 두면 한 번이 주기보다 오래 걸릴 때 다음 실행이 즉시
     * 이어 붙어 같은 전량 집계가 겹쳐 돈다.
     */
    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.setTaskScheduler(rankingTaskScheduler());
        registrar.addFixedDelayTask(job::refreshCurrent, properties.interval());
        registrar.addFixedDelayTask(job::refreshPrevious, properties.prevInterval());
    }
}
