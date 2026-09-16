package com.ssafy.motif.coreapi.domain.video.ranking;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * HOT 랭킹 배치 주기 등록 (API 명세 §6.1).
 *
 * <p><b>{@code registrar.setTaskScheduler()} 를 부르지 않는다.</b> 여러
 * {@link SchedulingConfigurer} 가 <b>같은</b> {@link ScheduledTaskRegistrar} 를 받으므로
 * 두 곳에서 스케줄러를 심으면 나중에 도는 쪽이 앞의 것을 조용히 덮는다 — 풀 설정이
 * 한쪽만 적용되고 예외도 경고도 없다. 스케줄러는 §5.1 의 {@code RankingConfig} 가
 * 소유하고(레포에 하나뿐이다) 여기서는 작업만 더한다. 대신 그쪽 풀 크기를 이 배치
 * 몫까지 올려 뒀다.
 *
 * <p><b>{@code @Scheduled} 를 쓰지 않는 이유</b>도 §5.1 과 같다 —
 * {@code fixedDelayString} 은 ISO-8601 이나 밀리초 정수만 받아서 설정에 팀이 쓰는
 * 표기({@code 5m})를 넣으면 기동 시점에 터진다. {@link java.time.Duration} 을 그대로
 * 넘겨 등록하면 문자열 파싱이 아예 없어진다.
 *
 * <p>{@code @EnableScheduling} 을 여기도 붙인다. {@code RankingConfig} 가 이미 켰지만
 * 거기에 기대면 §5.1 랭킹이 걷히는 날 <b>이 배치가 조용히 멈춘다</b>. 중복 선언은
 * 같은 후처리기 한 개로 합쳐져 부작용이 없다.
 */
@Configuration
@EnableScheduling
@RequiredArgsConstructor
@EnableConfigurationProperties(VideoRankingProperties.class)
public class VideoRankingConfig implements SchedulingConfigurer {

    private final VideoRankingProperties properties;
    private final VideoRankingSnapshotJob job;

    /**
     * {@code fixedRate} 가 아니라 {@code fixedDelay} 다 — 앞 실행이 끝난 뒤부터 주기를
     * 센다. {@code fixedRate} 로 두면 한 번이 주기보다 오래 걸릴 때 같은 전량 집계가
     * 겹쳐 돈다.
     */
    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addFixedDelayTask(job::refresh, properties.interval());
    }
}
