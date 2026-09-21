package com.ssafy.motif.coreapi.domain.video.view;

import com.ssafy.motif.coreapi.domain.video.ranking.VideoRankingSnapshotJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 조회수 집계 배치 (S15P21D104-130 · ADR-0015).
 *
 * <p><b>Redis·DB 없이 돈다.</b> 여기서 고정하는 것은 <b>단계 순서와 실패 시 남기는
 * 것</b>이고, Redis 명령의 실제 동작과 DB 반영은 {@code VideoViewFlushLocalDbTest} 가 본다.
 */
class VideoViewFlushJobTest {

    /** {@code yyyyMMddHHmmss} · UTC — 배치가 만드는 이름표와 같아야 한다. */
    private static final Instant NOW = Instant.parse("2026-09-21T09:40:12Z");
    private static final String TS = "20260921094012";

    private VideoViewBuffer buffer;
    private VideoViewCounterStore counterStore;
    private VideoRankingSnapshotJob rankingJob;
    private VideoViewFlushJob job;

    @BeforeEach
    void setUp() {
        buffer = mock(VideoViewBuffer.class);
        counterStore = mock(VideoViewCounterStore.class);
        rankingJob = mock(VideoRankingSnapshotJob.class);
        job = new VideoViewFlushJob(buffer, counterStore, rankingJob);

        given(buffer.unfinished()).willReturn(List.of());
    }

    @Test
    @DisplayName("정상 흐름 — rotate → 선점 → DB → 버킷 → processing 삭제 순서다")
    void runsStepsInOrder() {
        Map<Long, Long> counts = Map.of(101L, 3L, 102L, 1L);
        given(buffer.rotate(TS)).willReturn(true);
        given(buffer.readProcessing(TS)).willReturn(counts);
        given(buffer.claimFlush(TS)).willReturn(true);

        job.flush(NOW);

        InOrder order = inOrder(buffer, counterStore);
        order.verify(buffer).rotate(TS);
        order.verify(buffer).claimFlush(TS);
        order.verify(counterStore).add(counts);
        order.verify(buffer).saveBucket(TS, counts);
        // 삭제가 마지막이다 — 앞에서 죽으면 그 구간이 Redis 에 남아 다시 처리된다
        order.verify(buffer).discardProcessing(TS);
    }

    @Test
    @DisplayName("누적분이 없으면 아무것도 하지 않는다 — 조회 0건인 주기")
    void doesNothingWhenNothingPending() {
        given(buffer.rotate(TS)).willReturn(false);

        job.flush(NOW);

        verify(buffer, never()).claimFlush(anyString());
        verify(counterStore, never()).add(any());
        verify(buffer, never()).saveBucket(anyString(), any());
    }

    @Test
    @DisplayName("이미 DB 에 반영된 구간은 DB 를 건너뛰고 뒷정리만 한다")
    void skipsDbWhenAlreadyApplied() {
        Map<Long, Long> counts = Map.of(101L, 3L);
        given(buffer.rotate(TS)).willReturn(true);
        given(buffer.readProcessing(TS)).willReturn(counts);
        given(buffer.claimFlush(TS)).willReturn(false);

        job.flush(NOW);

        verify(counterStore, never()).add(any());
        // 버킷은 건너뛰지 않는다 — DB 는 됐는데 버킷 전에 죽은 재처리가 여기로 온다.
        // 건너뛰면 그 구간이 랭킹에서 통째로 빠진다
        verify(buffer).saveBucket(TS, counts);
        verify(buffer).discardProcessing(TS);
    }

    @Test
    @DisplayName("DB 가 실패하면 마커를 되돌리고 processing 을 남긴다")
    void releasesMarkerAndKeepsProcessingOnDbFailure() {
        Map<Long, Long> counts = Map.of(101L, 3L);
        given(buffer.rotate(TS)).willReturn(true);
        given(buffer.readProcessing(TS)).willReturn(counts);
        given(buffer.claimFlush(TS)).willReturn(true);
        willThrow(new IllegalStateException("db down")).given(counterStore).add(counts);

        job.flush(NOW);

        // 풀지 않으면 재처리가 "이미 반영됨" 으로 보고 건너뛰어 그 구간이 영영 사라진다
        verify(buffer).releaseFlush(TS);
        verify(buffer, never()).discardProcessing(TS);
        verify(buffer, never()).saveBucket(anyString(), any());
    }

    @Test
    @DisplayName("남은 구간을 먼저 처리한 뒤 이번 구간을 rotate 한다")
    void processesUnfinishedFirst() {
        String stale = "20260921092012";
        given(buffer.unfinished()).willReturn(List.of(stale));
        given(buffer.readProcessing(stale)).willReturn(Map.of(9L, 2L));
        given(buffer.claimFlush(stale)).willReturn(true);
        given(buffer.rotate(TS)).willReturn(true);
        given(buffer.readProcessing(TS)).willReturn(Map.of(101L, 3L));
        given(buffer.claimFlush(TS)).willReturn(true);

        job.flush(NOW);

        InOrder order = inOrder(buffer);
        order.verify(buffer).discardProcessing(stale);
        order.verify(buffer).rotate(TS);
        order.verify(buffer).discardProcessing(TS);
    }

    @Test
    @DisplayName("인덱스만 남은 빈 구간은 DB 를 건드리지 않고 정리한다")
    void cleansEmptyInterval() {
        String orphan = "20260921092012";
        given(buffer.unfinished()).willReturn(List.of(orphan));
        given(buffer.readProcessing(orphan)).willReturn(Map.of());
        given(buffer.rotate(TS)).willReturn(false);

        job.flush(NOW);

        verify(buffer, never()).claimFlush(orphan);
        verify(counterStore, never()).add(any());
        verify(buffer).discardProcessing(orphan);
    }

    @Test
    @DisplayName("예외를 삼킨다 — 스케줄이 죽지 않고 누적분은 Redis 에 남는다")
    void swallowsFailure() {
        willThrow(new IllegalStateException("redis down")).given(buffer).unfinished();

        assertThatCode(() -> job.flush(NOW)).doesNotThrowAnyException();

        verify(buffer, never()).rotate(eq(TS));
    }

    @Test
    @DisplayName("랭킹은 조회가 0건인 주기에도 돈다 — 구간이 밀리면 점수가 달라진다")
    void refreshesRankingEvenWithoutViews() {
        given(buffer.rotate(TS)).willReturn(false);

        job.flush(NOW);

        verify(rankingJob).refresh();
    }

    @Test
    @DisplayName("집계가 실패해도 랭킹은 돈다 — 두 단계를 따로 감싼다")
    void refreshesRankingAfterAggregationFailure() {
        willThrow(new IllegalStateException("redis down")).given(buffer).unfinished();

        job.flush(NOW);

        verify(rankingJob).refresh();
    }

    @Test
    @DisplayName("랭킹은 버킷을 저장한 뒤에 돈다 — 앞서면 한 틱 낡은 최근 조회수를 쓴다")
    void refreshesRankingAfterBucketIsSaved() {
        Map<Long, Long> counts = Map.of(101L, 3L);
        given(buffer.rotate(TS)).willReturn(true);
        given(buffer.readProcessing(TS)).willReturn(counts);
        given(buffer.claimFlush(TS)).willReturn(true);

        job.flush(NOW);

        InOrder order = inOrder(buffer, rankingJob);
        order.verify(buffer).saveBucket(TS, counts);
        order.verify(rankingJob).refresh();
    }
}
