package com.ssafy.motif.coreapi.domain.view;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * 조회수 집계 배치 주기 등록 (ADR-0015 · ADR-0016).
 *
 * <p><b>{@link ViewConfig} 와 나눠 둔 이유</b>는 순환 참조다 — 그쪽이 {@link ViewBuffer}
 * 빈을 만들고 {@link ViewFlushJob} 이 그 버퍼를 받으므로, 같은 클래스에서 job 을 주입받으면
 * {@code ViewConfig → ViewFlushJob → ViewBuffer → ViewConfig} 가 되어 기동이 실패한다.
 * <b>다시 합치지 말 것.</b>
 *
 * <p><b>{@code registrar.setTaskScheduler()} 를 부르지 않는다.</b> 여러
 * {@link SchedulingConfigurer} 가 <b>같은</b> {@link ScheduledTaskRegistrar} 를 받으므로
 * 두 곳에서 스케줄러를 심으면 나중에 도는 쪽이 앞의 것을 조용히 덮는다 — 풀 설정이
 * 한쪽만 적용되고 예외도 경고도 없다. 스케줄러는 §5.1 의 {@code RankingConfig} 가
 * 소유하고(레포에 하나뿐이다) 여기서는 작업만 더한다.
 *
 * <p><b>{@code @Scheduled} 를 쓰지 않는 이유</b> — {@code fixedDelayString} 은 ISO-8601
 * 이나 밀리초 정수만 받아서 설정에 팀이 쓰는 표기({@code 20m})를 넣으면 기동 시점에
 * 터진다. {@link java.time.Duration} 을 그대로 넘겨 등록하면 문자열 파싱이 아예 없어진다.
 */
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class ViewScheduleConfig implements SchedulingConfigurer {

    private final ViewProperties properties;
    private final ViewFlushJob job;

    /**
     * {@code fixedRate} 가 아니라 {@code fixedDelay} 다 — 앞 실행이 끝난 뒤부터 주기를
     * 센다. {@code fixedRate} 로 두면 한 번이 주기보다 오래 걸릴 때 같은 집계가 겹쳐
     * 돌고, rotate 가 {@code RENAMENX} 에서 막혀 아무 일도 못 하는 실행이 쌓인다.
     *
     * <p><b>벽시계에 정렬되지 않는다.</b> 구간 이름표가 실행 시각이라 드리프트가 쌓이면
     * {@code 09:00·09:20·09:40} 같은 경계에서 어긋나는데, <b>최근 구간을 이름으로
     * 계산하지 않고 인덱스 LIST 로 찾으므로 상관이 없다</b>(ADR-0015). 이 사실을 잊고
     * cron 으로 바꾸면 실행이 주기보다 길어질 때 겹쳐 돈다.
     */
    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addFixedDelayTask(job::flush, properties.interval());
    }
}
