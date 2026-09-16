package com.ssafy.motif.coreapi.domain.artist.ranking;

import com.ssafy.motif.domain.core.challenge.ChallengeScoreRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

/**
 * 랭킹 점수 공식 (명세 §5.1 · S15P21D104-51).
 *
 * <p>순수 계산이라 Redis·DB 가 필요 없다. 기준 시각을 주입받는 덕에 "30일 전 챌린지"를
 * 실제로 기다리지 않고 만들 수 있다.
 */
class RankingScoreTest {

    private static final Instant NOW = Instant.parse("2026-09-16T00:00:00Z");

    private static final int KARINA = 3;
    private static final int WINTER = 7;
    private static final int ROOKIE = 9;

    /** 운영 기본값과 같은 값 — 여기서 고정하는 것은 공식이지 숫자가 아니다. */
    private final RankingScore score = new RankingScore(new RankingProperties(
            new RankingProperties.Weight(1, 100), Duration.ofDays(14),
            Duration.ofMinutes(5), Duration.ofMinutes(10)));

    private static ChallengeScoreRow challenge(int artistSeq, long views, int participants,
                                               Duration age) {
        return new ChallengeScoreRow(artistSeq, views, participants, NOW.minus(age));
    }

    @Nested
    @DisplayName("반응 가중합")
    class Reaction {

        @Test
        @DisplayName("참여 1건은 조회 100회와 같은 무게다")
        void participantIsWorthHundredViews() {
            double byViews = score.of(challenge(KARINA, 100, 0, Duration.ZERO), NOW);
            double byParticipant = score.of(challenge(KARINA, 0, 1, Duration.ZERO), NOW);

            assertThat(byViews).isEqualTo(byParticipant);
        }

        @Test
        @DisplayName("반응이 0이면 챌린지가 있어도 0점이다 — 등록 자체에는 점수를 주지 않는다")
        void uploadingAloneScoresNothing() {
            Map<Integer, Double> scores = score.compute(
                    List.of(KARINA),
                    List.of(challenge(KARINA, 0, 0, Duration.ZERO),
                            challenge(KARINA, 0, 0, Duration.ZERO),
                            challenge(KARINA, 0, 0, Duration.ZERO)),
                    NOW);

            assertThat(scores).containsEntry(KARINA, 0.0);
        }
    }

    @Nested
    @DisplayName("나이 감쇠")
    class Decay {

        @Test
        @DisplayName("반감기가 지나면 절반이다")
        void halvesAfterHalfLife() {
            double fresh = score.of(challenge(KARINA, 1_000, 0, Duration.ZERO), NOW);
            double aged = score.of(challenge(KARINA, 1_000, 0, Duration.ofDays(14)), NOW);

            assertThat(aged).isCloseTo(fresh / 2, offset(0.001));
        }

        @Test
        @DisplayName("조회수가 3배 넘게 많아도 오래된 챌린지가 최근 챌린지에 밀린다")
        void olderChallengeLosesDespiteMoreViews() {
            double old = score.of(challenge(KARINA, 10_000, 0, Duration.ofDays(30)), NOW);
            double recent = score.of(challenge(KARINA, 3_000, 0, Duration.ofDays(2)), NOW);

            // 0.5^(30/14) = 0.2264 · 0.5^(2/14) = 0.9057
            assertThat(old).isCloseTo(2_264.0, offset(1.0));
            assertThat(recent).isCloseTo(2_717.0, offset(1.0));
            assertThat(recent).isGreaterThan(old);
        }

        @Test
        @DisplayName("감쇠는 챌린지마다 따로 걸린다 — 합친 뒤 깎지 않는다")
        void decaysPerChallenge() {
            // 같은 조회수 합(2,000)이라도 나이 구성이 다르면 점수가 달라야 한다
            double split = score.compute(List.of(KARINA),
                    List.of(challenge(KARINA, 1_000, 0, Duration.ZERO),
                            challenge(KARINA, 1_000, 0, Duration.ofDays(28))),
                    NOW).get(KARINA);
            double together = score.compute(List.of(KARINA),
                    List.of(challenge(KARINA, 2_000, 0, Duration.ofDays(14))),
                    NOW).get(KARINA);

            assertThat(split).isNotEqualTo(together);
        }

        @Test
        @DisplayName("등록 시각이 미래여도 계수는 1을 넘지 않는다")
        void futureChallengeIsNotBoosted() {
            double future = score.of(challenge(KARINA, 1_000, 0, Duration.ofDays(-3)), NOW);
            double now = score.of(challenge(KARINA, 1_000, 0, Duration.ZERO), NOW);

            assertThat(future).isEqualTo(now);
        }
    }

    @Nested
    @DisplayName("아티스트 묶기")
    class Grouping {

        @Test
        @DisplayName("챌린지가 없는 아티스트도 0점으로 결과에 들어간다")
        void rookieIsIncludedWithZero() {
            Map<Integer, Double> scores = score.compute(
                    List.of(KARINA, ROOKIE),
                    List.of(challenge(KARINA, 500, 0, Duration.ZERO)),
                    NOW);

            assertThat(scores).containsOnlyKeys(KARINA, ROOKIE);
            assertThat(scores).containsEntry(ROOKIE, 0.0);
        }

        @Test
        @DisplayName("한 아티스트의 챌린지 여러 개는 합산된다")
        void sumsPerArtist() {
            Map<Integer, Double> scores = score.compute(
                    List.of(KARINA, WINTER),
                    List.of(challenge(KARINA, 100, 0, Duration.ZERO),
                            challenge(KARINA, 250, 0, Duration.ZERO),
                            challenge(WINTER, 50, 0, Duration.ZERO)),
                    NOW);

            assertThat(scores).containsEntry(KARINA, 350.0);
            assertThat(scores).containsEntry(WINTER, 50.0);
        }

        @Test
        @DisplayName("스냅샷 대상이 아닌 아티스트의 챌린지는 버린다 — 삭제된 아티스트가 되살아나지 않는다")
        void dropsChallengesOfUnknownArtist() {
            Map<Integer, Double> scores = score.compute(
                    List.of(KARINA),
                    List.of(challenge(KARINA, 100, 0, Duration.ZERO),
                            challenge(WINTER, 9_999, 0, Duration.ZERO)),
                    NOW);

            assertThat(scores).containsOnlyKeys(KARINA);
        }
    }

    @Test
    @DisplayName("현재 데이터에서는 전 아티스트가 0점 동점이다 — 선행 티켓(§7.4·참여 업로드) 전 상태")
    void allZeroUntilUpstreamTicketsLand() {
        // view_count·participant_count 를 올리는 경로가 아직 없어 챌린지가 있어도 전부 0 이다.
        // 이 테스트는 그 상태가 '정상'임을 못 박는다 — 랭킹이 평탄하다고 공식을 의심하지 않도록.
        Map<Integer, Double> scores = score.compute(
                List.of(KARINA, WINTER, ROOKIE),
                List.of(challenge(KARINA, 0, 0, Duration.ofDays(1)),
                        challenge(WINTER, 0, 0, Duration.ofDays(5))),
                NOW);

        assertThat(scores.values()).containsOnly(0.0);
    }
}
