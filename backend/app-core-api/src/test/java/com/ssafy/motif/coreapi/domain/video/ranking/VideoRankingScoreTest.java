package com.ssafy.motif.coreapi.domain.video.ranking;

import com.ssafy.motif.coreapi.domain.video.view.VideoViewProperties;
import com.ssafy.motif.domain.core.video.VideoScoreRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HOT 점수 공식 (S15P21D104-130 · 명세 §6.1 · ADR-0015).
 *
 * <p>점수의 <b>절대값이 아니라 순서와 성질</b>을 고정한다 — 가중치·기준값은 설정이라
 * 값 자체를 박으면 조정할 때마다 테스트가 깨진다. 대신 "무엇이 무엇을 이겨야 하는가" 는
 * 설정을 바꿔도 유지돼야 하는 성질이다.
 */
class VideoRankingScoreTest {

    private static final double T0 = 10_000;
    private static final double R0 = 300;
    private static final double D0 = 3.0;
    private static final double K = 10;

    private final VideoRankingScore score = new VideoRankingScore(properties());

    private static VideoViewProperties properties() {
        return new VideoViewProperties(50, 100,
                Duration.ofMinutes(20), Duration.ofHours(6), 12, Duration.ofHours(24), 3,
                new VideoViewProperties.Ranking(500, Duration.ofHours(2), 3, K,
                        new VideoViewProperties.Weight(0.3, 0.5, 0.2),
                        new VideoViewProperties.Scale(T0, R0, D0)));
    }

    @Nested
    @DisplayName("이 공식이 존재하는 이유")
    class WhyThisFormula {

        /**
         * 구 공식({@code view_count × 0.5^(age/2d)})이라면 C 는 계수 0.03 에 눌려 절대
         * 올라오지 못한다. 시간 감쇠를 걷어낸 근거가 이 한 줄이다 — 깨지면 감쇠가
         * 되살아났거나 최근·증감 비중이 무너진 것이다.
         */
        @Test
        @DisplayName("오래된 영상이라도 지금 뜨면 1위다")
        void oldButSurgingWins() {
            double oldAndQuiet = score.of(100_000, 10, 15);     // 옛날 인기작, 지금 조용
            double steadyLarge = score.of(150_000, 600, 580);   // 꾸준한 대형
            double newAndRising = score.of(500, 500, 100);      // 신규 급상승
            double oldAndSurging = score.of(80_000, 450, 50);   // 옛날 영상이 떡상

            assertThat(oldAndSurging)
                    .isGreaterThan(steadyLarge)
                    .isGreaterThan(newAndRising)
                    .isGreaterThan(oldAndQuiet);
        }

        @Test
        @DisplayName("누적만 크고 지금 조용한 영상이 맨 아래다")
        void pastGloryLoses() {
            double oldAndQuiet = score.of(100_000, 10, 15);
            double newAndRising = score.of(500, 500, 100);

            assertThat(newAndRising).isGreaterThan(oldAndQuiet);
        }
    }

    @Nested
    @DisplayName("모멘텀은 절대 증감이 아니라 기존 값 대비다")
    class Momentum {

        @Test
        @DisplayName("같은 +100 이라도 작은 기반에서 난 쪽이 높다")
        void relativeBeatsAbsolute() {
            assertThat(score.momentum(150, 50))            // 50 → 150
                    .isGreaterThan(score.momentum(5_100, 5_000)); // 5000 → 5100
        }

        /**
         * 완충 상수 {@code K} 가 없으면 {@code 0 → 3} 의 비율이 발산해 노이즈가 1위를
         * 가져간다. 비율 방식의 고전적 함정이라 회귀로 박아 둔다.
         */
        @Test
        @DisplayName("0에서 3으로 오른 노이즈가 1000에서 2000으로 오른 영상을 이기지 못한다")
        void smallBaseDoesNotExplode() {
            assertThat(score.momentum(3, 0)).isLessThan(score.momentum(2_000, 1_000));
            assertThat(score.of(0, 3, 0)).isLessThan(score.of(0, 2_000, 1_000));
        }

        @Test
        @DisplayName("변동이 없으면 0.5 — 상승도 하락도 아니다")
        void flatIsNeutral() {
            assertThat(score.momentum(100, 100)).isEqualTo(0.5);
        }

        @Test
        @DisplayName("하락은 0.5 미만이고, 전체 점수를 음수로 만들지 않는다")
        void declineIsPenalisedButNotNegative() {
            assertThat(score.momentum(10, 1_000)).isLessThan(0.5).isGreaterThanOrEqualTo(0.0);
            assertThat(score.of(0, 0, 10_000)).isGreaterThanOrEqualTo(0.0);
        }

        @Test
        @DisplayName("폭등은 기준값에서 잘린다 — 한 영상이 전체를 삼키지 않는다")
        void surgeIsClamped() {
            assertThat(score.momentum(1_000_000, 1)).isEqualTo(score.momentum(10_000_000, 1));
        }
    }

    @Nested
    @DisplayName("경계")
    class Edges {

        @Test
        @DisplayName("조회가 전무하면 0점 근처지만 NaN 이 아니다 — 정렬이 무너지지 않는다")
        void allZeroIsFinite() {
            assertThat(score.of(0, 0, 0)).isFinite();
        }

        @Test
        @DisplayName("이전 구간이 없으면(서비스 초기) 최근이 많을수록 높다")
        void firstHourStillOrders() {
            assertThat(score.of(100, 500, 0)).isGreaterThan(score.of(100, 50, 0));
        }

        @Test
        @DisplayName("버킷에 없는 영상은 최근·이전을 0으로 보고 후보에 남는다")
        void missingBucketEntriesAreZero() {
            List<VideoScoreRow> rows = List.of(
                    new VideoScoreRow(1L, 500L), new VideoScoreRow(2L, 500L));

            Map<Long, Double> scores = score.compute(rows, Map.of(1L, 100L), Map.of());

            assertThat(scores).containsOnlyKeys(1L, 2L);
            assertThat(scores.get(1L)).isGreaterThan(scores.get(2L));
        }
    }
}
