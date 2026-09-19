package com.ssafy.motif.coreapi.domain.video.ranking;

import com.ssafy.motif.domain.core.video.VideoScoreRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * HOT 점수 공식 (명세 §6.1 · S15P21D104-113).
 *
 * <p>공식을 SQL 이 아니라 자바에 둔 이유가 이 테스트다 — 반감기를 설정으로 빼 놓고
 * 계산을 검증할 수 없으면 설정만 있고 근거가 없는 것이 된다.
 */
class VideoRankingScoreTest {

    private static final Instant NOW = Instant.parse("2026-09-10T00:00:00Z");
    private static final Duration HALF_LIFE = Duration.ofDays(2);

    private final VideoRankingScore score = new VideoRankingScore(
            new VideoRankingProperties(HALF_LIFE, Duration.ofMinutes(5), Duration.ofMinutes(15)));

    private static VideoScoreRow row(long videoSeq, long viewCount, Instant regDttm) {
        return new VideoScoreRow(videoSeq, viewCount, regDttm);
    }

    @Test
    @DisplayName("갓 게시된 영상은 조회수가 그대로 점수다")
    void freshVideoScoresItsViewCount() {
        assertThat(score.of(row(1L, 3400L, NOW), NOW)).isEqualTo(3400.0);
    }

    @Test
    @DisplayName("반감기만큼 지나면 절반이다")
    void halvesAfterOneHalfLife() {
        VideoScoreRow old = row(1L, 3400L, NOW.minus(HALF_LIFE));

        assertThat(score.of(old, NOW)).isCloseTo(1700.0, within(0.001));
    }

    @Test
    @DisplayName("반감기 두 번이면 4분의 1이다")
    void quartersAfterTwoHalfLives() {
        VideoScoreRow old = row(1L, 3400L, NOW.minus(HALF_LIFE.multipliedBy(2)));

        assertThat(score.of(old, NOW)).isCloseTo(850.0, within(0.001));
    }

    @Test
    @DisplayName("오래됐지만 많이 본 영상이 최근의 적게 본 영상에 밀린다 — 감쇠의 요점")
    void decayLetsRecentVideoOvertakeOlderPopularOne() {
        VideoScoreRow popularButOld = row(1L, 10_000L, NOW.minus(Duration.ofDays(10)));
        VideoScoreRow freshButSmaller = row(2L, 1_000L, NOW.minus(Duration.ofHours(6)));

        assertThat(score.of(freshButSmaller, NOW)).isGreaterThan(score.of(popularButOld, NOW));
    }

    @Test
    @DisplayName("미래 시각은 계수를 1.0 으로 자른다 — 조회수를 부풀리지 않는다")
    void clampsFutureTimestampToOne() {
        // 자르지 않으면 반감기만큼 앞선 시각에서 계수가 2.0 이 되어 조회수가 2배로 잡힌다
        VideoScoreRow future = row(1L, 3400L, NOW.plus(HALF_LIFE));

        assertThat(score.of(future, NOW)).isEqualTo(3400.0);
    }

    @Test
    @DisplayName("게시 직후 1초 미만도 계수 1.0 이다 — 극한값이라 값이 바뀌지 않는다")
    void subSecondAgeIsUnchanged() {
        VideoScoreRow justNow = row(1L, 100L, NOW.minusMillis(900));

        assertThat(score.of(justNow, NOW)).isEqualTo(100.0);
    }

    @Test
    @DisplayName("조회수 0인 영상도 0점으로 결과에 들어간다 — 빼면 HOT 에 영영 못 오른다")
    void keepsZeroViewVideos() {
        Map<Long, Double> scores = score.compute(
                List.of(row(1L, 0L, NOW), row(2L, 50L, NOW)), NOW);

        assertThat(scores).containsOnlyKeys(1L, 2L);
        assertThat(scores.get(1L)).isZero();
    }

    @Test
    @DisplayName("후보가 없으면 빈 map 이다")
    void emptyCandidatesGiveEmptyScores() {
        assertThat(score.compute(List.of(), NOW)).isEmpty();
    }
}
