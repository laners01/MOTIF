package com.ssafy.motif.coreapi.domain.video.ranking;

import com.ssafy.motif.domain.core.video.ParticipationVideoRepository;
import com.ssafy.motif.domain.core.video.VideoScoreRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * HOT 랭킹 배치 (명세 §6.1 · S15P21D104-113).
 *
 * <p>여기서 고정하는 것은 <b>모집단 필터와 순서 확정</b>이다. 점수 공식 자체는
 * {@link VideoRankingScoreTest} 가 본다.
 */
class VideoRankingSnapshotJobTest {

    private static final Instant NOW = Instant.now();

    private ParticipationVideoRepository participationVideoRepository;
    private VideoRankingSnapshot snapshot;
    private VideoRankingSnapshotJob job;

    @BeforeEach
    void setUp() {
        participationVideoRepository = mock(ParticipationVideoRepository.class);
        snapshot = mock(VideoRankingSnapshot.class);
        job = new VideoRankingSnapshotJob(participationVideoRepository,
                new VideoRankingScore(new VideoRankingProperties(
                        Duration.ofDays(2), Duration.ofMinutes(5), Duration.ofMinutes(15))),
                snapshot);
    }

    private static VideoScoreRow row(long videoSeq, long viewCount) {
        return new VideoScoreRow(videoSeq, viewCount, NOW);
    }

    @SuppressWarnings("unchecked")
    private List<Long> capturedOrder() {
        ArgumentCaptor<List<Long>> captor = ArgumentCaptor.forClass(List.class);
        verify(snapshot).replace(captor.capture());
        return captor.getValue();
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
    @DisplayName("동점이면 videoSeq 오름차순이다 — 먼저 게시된 것이 앞이다")
    void breaksTiesByVideoSeqAscending() {
        // 조회수가 전부 0인 시연 초기 상태가 정확히 이 모양이다
        given(participationVideoRepository.findRankingInputs()).willReturn(List.of(
                row(88L, 0L), row(7L, 0L), row(12L, 0L)));

        job.refresh();

        assertThat(capturedOrder()).containsExactly(7L, 12L, 88L);
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
