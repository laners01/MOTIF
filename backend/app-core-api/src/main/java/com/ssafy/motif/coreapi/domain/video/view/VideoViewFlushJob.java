package com.ssafy.motif.coreapi.domain.video.view;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 조회수 집계 배치 (ADR-0015) — 기본 20분.
 *
 * <pre>
 * 0) 남은 구간부터 처리한다 (재처리 진입점)
 * 1) RENAMENX  pending → processing:{ts}
 * 2) SETNX     flush:applied:{ts}  — 이미 있으면 DB 를 건너뛴다
 * 3) DB 일괄 반영 (문장 하나)
 * 4) 버킷 저장 + 인덱스 갱신
 * 5) processing 삭제
 * </pre>
 *
 * <p><b>{@code processing} 을 마지막에 지운다.</b> 3~4 중 어디서 죽어도 그 구간이 Redis 에
 * 남아 다음 실행이 다시 집는다. 그때 DB 를 두 번 더하지 않는 것은 2)의 마커가 맡는다.
 *
 * <p><b>ShedLock 을 쓰지 않는다.</b> core-api 는 1 레플리카고(ADR-0009), 다중화되더라도
 * {@code RENAMENX} 가 rotate 를 한 쪽만 성공시키고 마커가 DB 이중 가산을 막는다. 다만
 * 기존 랭킹 배치들의 근거("전량 재계산이라 중복 실행돼도 결과가 같다")는 <b>여기에
 * 적용되지 않는다</b> — 이 배치는 누적 연산이다. 둘을 같은 이유로 묶어 생각하지 말 것.
 *
 * <p><b>예외를 삼킨다.</b> 스케줄된 작업이 예외를 던지면 다음 주기에 다시 도는 것이
 * 맞지만, Redis·DB 중 어디서 실패했는지가 스택만으로는 잘 안 보여 여기서 잡아 남긴다
 * ({@code VideoRankingSnapshotJob} 과 같은 판단). 실패해도 {@code processing} 이 남아
 * 있으므로 조회수를 잃지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VideoViewFlushJob {

    /**
     * 구간 이름표 — {@code 20260921094012}.
     *
     * <p><b>초까지 넣는다.</b> 분 단위로 자르면 시연에서 {@code interval} 을 1분 아래로
     * 내렸을 때 같은 이름이 나오고, 그러면 rotate 가 {@code RENAMENX} 에서 계속 실패해
     * 집계가 멈춘다.
     *
     * <p>이 문자열로 <b>키를 계산하지는 않는다</b> — 최근 구간을 찾는 것은 인덱스 LIST 의
     * 몫이라 이름표는 사람이 읽기 위한 것이다(ADR-0015). 그래서 스케줄러가 벽시계에
     * 정렬되지 않아도 상관없다.
     */
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
            .withZone(ZoneOffset.UTC);

    private final VideoViewBuffer buffer;
    private final VideoViewCounterStore counterStore;

    /** 스케줄러가 부르는 자리. */
    public void flush() {
        flush(Instant.now());
    }

    /**
     * 기준 시각을 파라미터로 받는다 — 안에서 {@code Instant.now()} 를 부르면 테스트가
     * 구간 이름을 고정할 수 없다({@code VideoRankingScore} 와 같은 방식).
     */
    public void flush(Instant now) {
        try {
            // 남은 것을 먼저 비운다 — rotate 가 앞 구간과 같은 이름을 쓸 일은 없지만,
            // 밀린 구간을 쌓아 두면 "최근 3개 버킷" 이 실제 최근을 못 가리킨다
            List<String> unfinished = buffer.unfinished();
            if (!unfinished.isEmpty()) {
                log.info("미완료 조회수 구간 {}건을 먼저 처리한다 — {}", unfinished.size(), unfinished);
                unfinished.forEach(this::process);
            }

            String ts = TS.format(now);
            if (!buffer.rotate(ts)) {
                // 이 주기에 조회가 0건이었거나 앞 구간이 아직 남아 있다
                log.debug("조회수 집계 건너뜀 — 누적분이 없다 ({})", ts);
                return;
            }
            process(ts);
        } catch (Exception e) {
            log.error("조회수 집계 실패 — 누적분은 Redis 에 남아 다음 주기가 다시 집는다", e);
        }
    }

    /**
     * 얼린 구간 하나를 끝까지 처리한다.
     *
     * <p><b>DB 가 실패하면 마커를 되돌린다.</b> 선점만 하고 반영에 실패한 채 마커가 남으면
     * 재처리가 "이미 반영됨" 으로 보고 조용히 건너뛰어 <b>그 구간이 영영 사라진다</b>.
     * 알림 쪽 {@code EventIdempotencyGuard.release} 와 같은 이유다.
     *
     * <p><b>버킷은 DB 반영을 건너뛰었어도 저장한다.</b> 3)까지 성공하고 4)에서 죽은
     * 재처리가 여기로 오는데, 그때 버킷을 건너뛰면 그 구간이 랭킹에서 통째로 빠진다.
     * 덮어쓰기라 두 번 써도 같다.
     */
    private void process(String ts) {
        Map<Long, Long> counts = buffer.readProcessing(ts);

        if (counts.isEmpty()) {
            // 인덱스만 남은 구간 — rotate 가 인덱스를 먼저 넣고 rename 전에 죽으면 생긴다
            log.info("빈 조회수 구간을 정리한다 — {}", ts);
            buffer.discardProcessing(ts);
            return;
        }

        if (buffer.claimFlush(ts)) {
            try {
                int updated = counterStore.add(counts);
                log.info("조회수 DB 반영 — ts={} 영상 {}건 중 {}행 갱신", ts, counts.size(), updated);
            } catch (RuntimeException e) {
                buffer.releaseFlush(ts);
                throw e;
            }
        } else {
            log.warn("이미 DB 에 반영된 구간이다 — DB 를 건너뛰고 뒷정리만 한다 ({})", ts);
        }

        buffer.saveBucket(ts, counts);
        buffer.discardProcessing(ts);
    }
}
