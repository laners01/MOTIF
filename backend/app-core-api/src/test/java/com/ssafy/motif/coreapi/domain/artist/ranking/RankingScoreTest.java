package com.ssafy.motif.coreapi.domain.artist.ranking;

import com.ssafy.motif.domain.core.challenge.ChallengeScoreRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 아티스트 랭킹 점수 공식 (명세 §5.1 · ADR-0016).
 *
 * <p>점수의 <b>절대값이 아니라 순서와 성질</b>을 고정한다 — 가중치·기준값은 설정이라
 * 값 자체를 박으면 조정할 때마다 테스트가 깨진다. 대신 "무엇이 무엇을 이겨야 하는가" 는
 * 설정을 바꿔도 유지돼야 하는 성질이다.
 */
class RankingScoreTest {

    private static final int KARINA = 3;
    private static final int WINTER = 11;
    private static final int NINGNING = 21;

    private final RankingScore score = new RankingScore(properties());

    private static RankingProperties properties() {
        return new RankingProperties(
                new RankingProperties.Reaction(1, 100),
                new RankingProperties.Weight(0.5, 0.35, 0.15),
                new RankingProperties.Scale(500_000, 5_000, 5.0),
                100, Duration.ofHours(2), 3);
    }

    private static ChallengeScoreRow challenge(int challengeSeq, int artistSeq,
                                               long views, int participants) {
        return new ChallengeScoreRow(challengeSeq, artistSeq, views, participants);
    }

    @Nested
    @DisplayName("이 공식이 존재하는 이유")
    class WhyThisFormula {

        /**
         * 구 공식({@code Σ(…) × 0.5^(age/14d)})이라면 오래된 챌린지는 계수에 눌려 지금
         * 아무리 돌아도 올라오지 못한다. 시간 감쇠를 걷어낸 근거가 이 한 줄이다.
         */
        @Test
        @DisplayName("지금 뜨는 중형이 1위, 대형인데 조용한 쪽이 2위, 신인이 3위다")
        void risingBeatsLargeWhichBeatsRookie() {
            double rising = score.of(120_000, 3_000, 500);   // 지금 뜨는 중형
            double large = score.of(700_000, 200, 250);      // 대형인데 조용
            double rookie = score.of(3_000, 800, 100);       // 신인 급상승

            assertThat(rising).isGreaterThan(large);
            assertThat(large).isGreaterThan(rookie);
        }

        /**
         * §6.1 가중치(0.3/0.5/0.2)를 그대로 쓰면 신인이 대형을 앞선다. 이 스냅샷을
         * §3.1 온보딩 인기 목록이 함께 쓰므로 무명이 1위로 뜨면 곤란하다(ADR-0016).
         */
        @Test
        @DisplayName("누적 비중이 커서 대형 아티스트가 신인에게 밀리지 않는다")
        void cumulativeKeepsEstablishedArtistsAhead() {
            assertThat(score.of(700_000, 200, 250)).isGreaterThan(score.of(3_000, 800, 100));
        }
    }

    @Nested
    @DisplayName("참여 영상 수는 누적 항에만 들어간다")
    class Participants {

        @Test
        @DisplayName("참여가 많은 아티스트의 누적 반응량이 더 크다")
        void participantsRaiseTheCumulativeTerm() {
            Map<Integer, Double> scores = score.compute(
                    List.of(KARINA, WINTER),
                    List.of(challenge(1, KARINA, 1_000, 50), challenge(2, WINTER, 1_000, 0)),
                    Map.of(), Map.of());

            assertThat(scores.get(KARINA)).isGreaterThan(scores.get(WINTER));
        }

        /**
         * 1시간 창에 참여를 더하면 {@code Wp=100} 이 환산비가 아니라 on/off 스위치가 된다 —
         * 조회가 2.5배 많은 아티스트가 참여 1건에 진다. 우리 규모에서 1시간 참여는 0 아니면
         * 1 이라 순위가 동전 던지기가 된다(ADR-0016). <b>이 테스트가 깨지면 참여가 최근
         * 항으로 새어 들어간 것이다.</b>
         */
        @Test
        @DisplayName("최근 조회수가 많은 쪽이 이긴다 — 참여가 최근 항으로 새지 않는다")
        void participantsDoNotLeakIntoRecentTerm() {
            Map<Integer, Double> scores = score.compute(
                    List.of(KARINA, WINTER),
                    // 누적 반응량을 10,000 으로 맞춘다 — KARINA 는 조회로, WINTER 는
                    // 조회 5,000 + 참여 50건(×100)으로. 남는 차이는 최근 조회수뿐이다
                    List.of(challenge(1, KARINA, 10_000, 0), challenge(2, WINTER, 5_000, 50)),
                    Map.of(1L, 50L, 2L, 20L), Map.of());

            // 참여가 최근 항에 더해지면 WINTER 의 최근이 20 + 5,000 이 되어 뒤집힌다
            assertThat(scores.get(KARINA)).isGreaterThan(scores.get(WINTER));
        }
    }

    @Nested
    @DisplayName("모멘텀은 절대 증감이 아니라 기존 값 대비다")
    class Momentum {

        @Test
        @DisplayName("같은 +100 이라도 작은 기반에서 난 쪽이 높다")
        void relativeBeatsAbsolute() {
            assertThat(score.momentum(150, 50))
                    .isGreaterThan(score.momentum(5_100, 5_000));
        }

        /** {@code K} 가 없으면 작은 분모에서 비율이 발산해 노이즈가 1위를 가져간다. */
        @Test
        @DisplayName("0에서 3으로 오른 노이즈가 1000에서 2000으로 오른 쪽을 이기지 못한다")
        void smallBaseDoesNotExplode() {
            assertThat(score.momentum(3, 0)).isLessThan(score.momentum(2_000, 1_000));
        }

        @Test
        @DisplayName("변동이 없으면 0.5 — 상승도 하락도 아니다")
        void flatIsNeutral() {
            assertThat(score.momentum(100, 100)).isEqualTo(0.5);
        }

        @Test
        @DisplayName("하락은 0.5 미만이고, 전체 점수를 음수로 만들지 않는다")
        void declineIsPenalisedButNotNegative() {
            assertThat(score.momentum(10, 5_000)).isLessThan(0.5).isGreaterThanOrEqualTo(0.0);
            assertThat(score.of(0, 0, 10_000)).isGreaterThanOrEqualTo(0.0);
        }

        @Test
        @DisplayName("폭등은 기준값에서 잘린다 — 한 아티스트가 전체를 삼키지 않는다")
        void surgeIsClamped() {
            assertThat(score.momentum(1_000_000, 1)).isEqualTo(score.momentum(10_000_000, 1));
        }
    }

    @Nested
    @DisplayName("모집단")
    class Population {

        /**
         * 빼면 온보딩 인기 목록(§3.1)과 {@code UNSUBSCRIBED} 필터에서 신인이 통째로
         * 사라진다 — §6.1 의 {@code view_count > 0} 필터를 여기에 복사하면 안 되는 이유다.
         */
        @Test
        @DisplayName("챌린지가 0개인 아티스트도 0점으로 들어간다")
        void artistWithoutChallengesIsIncluded() {
            Map<Integer, Double> scores = score.compute(
                    List.of(KARINA, WINTER), List.of(challenge(1, KARINA, 100, 1)),
                    Map.of(), Map.of());

            assertThat(scores).containsOnlyKeys(KARINA, WINTER);
            assertThat(scores.get(WINTER)).isNotNull();
            assertThat(scores.get(KARINA)).isGreaterThan(scores.get(WINTER));
        }

        @Test
        @DisplayName("스냅샷에 없는 아티스트의 챌린지는 버린다 — 유령 항목을 만들지 않는다")
        void challengeOfMissingArtistIsDropped() {
            Map<Integer, Double> scores = score.compute(
                    List.of(KARINA),
                    List.of(challenge(1, KARINA, 100, 0), challenge(2, NINGNING, 999_999, 0)),
                    Map.of(), Map.of());

            assertThat(scores).containsOnlyKeys(KARINA);
        }

        @Test
        @DisplayName("한 아티스트의 챌린지 여러 개가 합산된다")
        void challengesAreSummedPerArtist() {
            Map<Integer, Double> scores = score.compute(
                    List.of(KARINA, WINTER),
                    List.of(challenge(1, KARINA, 500, 0), challenge(2, KARINA, 500, 0),
                            challenge(3, WINTER, 900, 0)),
                    Map.of(), Map.of());

            assertThat(scores.get(KARINA)).isGreaterThan(scores.get(WINTER));
        }

        @Test
        @DisplayName("최근 조회수는 챌린지별 버킷에서 아티스트로 합산된다")
        void recentViewsAreSummedFromBuckets() {
            Map<Integer, Double> scores = score.compute(
                    List.of(KARINA, WINTER),
                    List.of(challenge(1, KARINA, 1_000, 0), challenge(2, KARINA, 1_000, 0),
                            challenge(3, WINTER, 2_000, 0)),
                    Map.of(1L, 300L, 2L, 300L, 3L, 100L), Map.of());

            assertThat(scores.get(KARINA)).isGreaterThan(scores.get(WINTER));
        }
    }

    @Nested
    @DisplayName("경계")
    class Edges {

        @Test
        @DisplayName("반응이 전무하면 0점 근처지만 NaN 이 아니다 — 정렬이 무너지지 않는다")
        void allZeroIsFinite() {
            assertThat(score.of(0, 0, 0)).isFinite();
        }

        @Test
        @DisplayName("이전 구간이 없으면(서비스 초기) 최근이 많을수록 높다")
        void firstHourStillOrders() {
            assertThat(score.of(1_000, 800, 0)).isGreaterThan(score.of(1_000, 80, 0));
        }
    }
}
