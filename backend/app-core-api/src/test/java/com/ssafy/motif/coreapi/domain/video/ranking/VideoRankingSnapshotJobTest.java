package com.ssafy.motif.coreapi.domain.video.ranking;

import com.ssafy.motif.coreapi.domain.view.ViewBuffer;
import com.ssafy.motif.coreapi.domain.view.ViewProperties;
import com.ssafy.motif.domain.core.video.ParticipationVideoRepository;
import com.ssafy.motif.domain.core.video.VideoScoreRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * HOT 랭킹 배치 (명세 §6.1 · S15P21D104-130).
 *
 * <p>여기서 고정하는 것은 <b>재료를 어디서 가져오는가·순서 확정·상한</b>이다. 점수 공식
 * 자체는 {@link VideoRankingScoreTest} 가 본다.
 */
class VideoRankingSnapshotJobTest {

    private static final int RECENT_BUCKETS = 3;
    private static final int KEEP = 500;

    private ParticipationVideoRepository participationVideoRepository;
    private ViewBuffer buffer;
    private VideoRankingSnapshot snapshot;
    private VideoRankingSnapshotJob job;

    @BeforeEach
    void setUp() {
        participationVideoRepository = mock(ParticipationVideoRepository.class);
        buffer = mock(ViewBuffer.class);
        snapshot = mock(VideoRankingSnapshot.class);

        ViewProperties properties = properties(KEEP);
        given(buffer.sumBuckets(anyInt(), anyInt())).willReturn(Map.of());

        job = new VideoRankingSnapshotJob(participationVideoRepository, buffer,
                new VideoRankingScore(properties), snapshot, properties);
    }

    private static ViewProperties properties(int keep) {
        return new ViewProperties(50, 100,
                Duration.ofMinutes(20), Duration.ofHours(6), 12, Duration.ofHours(24),
                RECENT_BUCKETS,
                new ViewProperties.Ranking(keep, Duration.ofHours(2), 3, 10,
                        new ViewProperties.Weight(0.3, 0.5, 0.2),
                        new ViewProperties.Scale(10_000, 300, 3.0)));
    }

    private static VideoScoreRow row(long videoSeq, long viewCount) {
        return new VideoScoreRow(videoSeq, viewCount);
    }

    @SuppressWarnings("unchecked")
    private List<Long> capturedOrder() {
        ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
        verify(snapshot).replace(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("최근 구간과 그 이전 구간을 버킷 인덱스에서 연달아 읽는다")
    void readsRecentAndPreviousWindows() {
        given(participationVideoRepository.findRankingInputs()).willReturn(List.of(row(7L, 10L)));

        job.refresh();

        // 0~2 가 최근 1시간, 3~5 가 이전 1시간이다 — 겹치거나 비면 증감이 통째로 틀린다
        verify(buffer).sumBuckets(eq(0), eq(RECENT_BUCKETS));
        verify(buffer).sumBuckets(eq(RECENT_BUCKETS), eq(RECENT_BUCKETS));
    }

    @Test
    @DisplayName("점수 내림차순으로 순서를 확정한다")
    void ordersByScoreDescending() {
        given(participationVideoRepository.findRankingInputs()).willReturn(List.of(
                row(7L, 10L), row(88L, 500L), row(12L, 120L)));

        job.refresh();

        assertThat(capturedOrder()).containsExactly(88L, 12L, 7L);
    }

    @Test
    @DisplayName("최근 조회수가 순위를 뒤집는다 — 누적만으로 결정되지 않는다")
    void recentViewsOutweighTotals() {
        given(participationVideoRepository.findRankingInputs()).willReturn(List.of(
                row(7L, 100_000L), row(88L, 500L)));
        given(buffer.sumBuckets(0, RECENT_BUCKETS)).willReturn(Map.of(88L, 800L));
        given(buffer.sumBuckets(RECENT_BUCKETS, RECENT_BUCKETS)).willReturn(Map.of(88L, 50L));

        job.refresh();

        assertThat(capturedOrder()).containsExactly(88L, 7L);
    }

    @Test
    @DisplayName("동점이면 videoSeq 오름차순이다 — 먼저 게시된 것이 앞이다")
    void breaksTiesByVideoSeqAscending() {
        given(participationVideoRepository.findRankingInputs()).willReturn(List.of(
                row(88L, 30L), row(7L, 30L), row(12L, 30L)));

        job.refresh();

        assertThat(capturedOrder()).containsExactly(7L, 12L, 88L);
    }

    @Test
    @DisplayName("상위 keep 개만 담는다 — 점수 임계로 자르지 않는다")
    void keepsOnlyTopN() {
        ViewProperties small = properties(2);
        job = new VideoRankingSnapshotJob(participationVideoRepository, buffer,
                new VideoRankingScore(small), snapshot, small);
        given(participationVideoRepository.findRankingInputs()).willReturn(
                LongStream.rangeClosed(1, 5).mapToObj(seq -> row(seq, seq * 100)).toList());

        job.refresh();

        assertThat(capturedOrder()).containsExactly(5L, 4L);
    }

    @Test
    @DisplayName("키 프리픽스로 거르지 않는다 — 참여 영상은 전부 보호 등급이다 (ADR-0013)")
    void doesNotFilterByKeyPrefix() {
        // 한때 보호 프리픽스를 떨어뜨리는 필터가 있었다. 되살리면 후보가 전멸한다
        given(participationVideoRepository.findRankingInputs()).willReturn(List.of(
                row(88L, 500L), row(12L, 120L)));

        job.refresh();

        assertThat(capturedOrder()).containsExactly(88L, 12L);
    }

    @Test
    @DisplayName("후보가 0건이면 포인터를 지운다 — 빈 스냅샷을 적재하지 않는다")
    void clearsPointerWhenNoCandidates() {
        given(participationVideoRepository.findRankingInputs()).willReturn(List.of());

        job.refresh();

        verify(snapshot).clear();
        verify(snapshot, never()).replace(anyList());
    }

    @Test
    @DisplayName("실패해도 예외를 던지지 않는다 — 스케줄이 죽지 않고 직전 스냅샷이 남는다")
    void swallowsFailureSoScheduleSurvives() {
        given(participationVideoRepository.findRankingInputs())
                .willThrow(new IllegalStateException("DB 장애"));

        job.refresh();

        verify(snapshot, never()).replace(anyList());
        verify(snapshot, never()).clear();
    }
}
