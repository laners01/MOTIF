package com.ssafy.motif.coreapi.domain.artist.ranking;

import com.ssafy.motif.domain.core.challenge.ChallengeScoreRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * 아티스트 랭킹 점수 공식 (API 명세 §5.1 · ADR-0016) — <b>순수 계산</b>.
 *
 * <pre>
 * 아티스트 a 의 비삭제 챌린지 c 들에 대해
 *   reaction_total  = Σ_c ( view_count_c + Wp × participant_count_c )
 *   reaction_recent = Σ_c ( 최근 구간 조회수_c )
 *   reaction_prev   = Σ_c ( 그 이전 구간 조회수_c )
 *
 * tScore = log10(1 + reaction_total)  / log10(1 + T0)
 * rScore = log10(1 + reaction_recent) / log10(1 + R0)
 * dRel   = (reaction_recent − reaction_prev) / (reaction_prev + K)
 * dScore = 0.5 + 0.5 × clamp(dRel / D0, −1, +1)
 *
 * score  = Wt·tScore + Wr·rScore + Wd·dScore
 * </pre>
 *
 * <p><b>§6.1 {@code VideoRankingScore} 와 같은 구조다.</b> 다른 것은 재료를 아티스트
 * 단위로 먼저 합친다는 것과 가중치·기준값뿐이다.
 *
 * <p><b>참여 영상 수는 누적 항에만 들어간다.</b> {@code Wp}("참여 1건 = 조회 100회")는
 * <b>누적끼리</b> 비교하려고 정한 값이라, 1시간 창에서는 참여가 정수 0~2건이라 환산비가
 * 아니라 <b>on/off 스위치</b>가 된다 — 1시간 조회 50·참여 0 인 아티스트가 조회 20·참여
 * 1 인 아티스트에게 진다. 우리 규모에서 순위가 "최근 한 시간에 누가 영상을 올렸나" 로
 * 결정되면 동전 던지기다(ADR-0016). <b>최근·증감 항에 참여를 더하지 말 것.</b>
 *
 * <p><b>시간 감쇠가 없다.</b> 예전 공식은 {@code Σ(…) × 0.5^(age_c/H)} 였고 나이의 기준이
 * 챌린지 등록 시각이었다 — 누적 카운터뿐이라 걸 수 있는 시간축이 그것뿐이었기 때문이다.
 * 버킷이 "지금 얼마나 보고 있는가" 를 직접 주면서 필요가 없어졌고, 무엇보다 <b>감쇠를
 * 남기면 오래된 챌린지가 지금 아무리 돌아도 올라오지 못한다</b>.
 *
 * <p><b>다만 성격이 바뀌었다.</b> 감쇠는 <b>챌린지 하나하나</b>에 걸려 "옛 챌린지 vs 새
 * 챌린지" 를 갈랐다. 지금은 아티스트 단위로 합산한 뒤 최근·증감을 보므로 <b>"이 아티스트가
 * 지금 뜨는가" 만 남는다</b> — 아티스트 랭킹의 목적에는 이쪽이 맞다.
 *
 * <p><b>챌린지 등록 자체에는 점수를 주지 않는다.</b> 점수의 출처는 챌린지가 실제로 받은
 * 반응뿐이다 — 등록에 점수를 주면 반응 없는 챌린지를 여러 개 올리는 것만으로 순위가 올라
 * 인기 랭킹이 아니라 활동량 랭킹이 된다.
 */
@Component
@RequiredArgsConstructor
public class RankingScore {

    private final RankingProperties properties;

    /**
     * 아티스트별 점수를 낸다.
     *
     * @param artistSeqs 비삭제 아티스트 전량. <b>챌린지가 0개인 아티스트도 0점으로
     *                   결과에 들어간다</b> — 챌린지 쪽에서만 모으면 신인이 스냅샷에서
     *                   빠져 온보딩 목록(§3.1)과 {@code UNSUBSCRIBED} 필터에서 사라진다
     * @param challenges 비삭제 챌린지 전량
     * @param recent     {@code challengeSeq → 최근 구간 조회수} (Redis 버킷)
     * @param previous   {@code challengeSeq → 그 이전 구간 조회수}
     */
    public Map<Integer, Double> compute(Collection<Integer> artistSeqs,
                                        Collection<ChallengeScoreRow> challenges,
                                        Map<Long, Long> recent,
                                        Map<Long, Long> previous) {
        Map<Integer, Reaction> reactions = new HashMap<>();
        artistSeqs.forEach(artistSeq -> reactions.put(artistSeq, new Reaction()));

        for (ChallengeScoreRow challenge : challenges) {
            // 스냅샷에 없는 아티스트(삭제됨)의 챌린지는 버린다 — 되살리면 메타 조회에서
            // 이름이 안 나오는 유령 항목이 랭킹에 뜬다
            Reaction reaction = reactions.get(challenge.artistSeq());
            if (reaction == null) {
                continue;
            }
            long challengeSeq = challenge.challengeSeq();
            double view = properties.reaction().view();
            reaction.total += (long) (challenge.viewCount() * view
                    + challenge.participantCount() * properties.reaction().participant());
            // 최근·이전에는 참여를 더하지 않는다 — 클래스 javadoc 참조
            reaction.recent += (long) (recent.getOrDefault(challengeSeq, 0L) * view);
            reaction.previous += (long) (previous.getOrDefault(challengeSeq, 0L) * view);
        }

        Map<Integer, Double> scores = new HashMap<>();
        reactions.forEach((artistSeq, reaction) ->
                scores.put(artistSeq, of(reaction.total, reaction.recent, reaction.previous)));
        return scores;
    }

    /**
     * 아티스트 한 명의 점수 — 세 항의 가중합.
     *
     * <p><b>반응이 전무해도 0점이 아니다.</b> 모멘텀의 "변동 없음" 이 0.5 라 누구나
     * {@code Wd × 0.5} 만큼을 바닥으로 받는다(기본 0.075). 반응이 0인 아티스트끼리는
     * 전부 같은 값이라 순서는 {@code artistSeq} 오름차순으로 확정되고, 반응이 있는
     * 아티스트는 누적 항만으로도 이 바닥을 넘는다 — 순위에 영향이 없다.
     */
    public double of(long total, long recent, long previous) {
        RankingProperties.Weight weight = properties.weight();
        return weight.total() * normalized(total, properties.scale().total())
                + weight.recent() * normalized(recent, properties.scale().recent())
                + weight.delta() * momentum(recent, previous);
    }

    /**
     * 규모 항 — {@code log10} 으로 눌러 기준값과 견준다.
     *
     * <p>{@code log} 를 씌우는 것은 반응량 분포가 한쪽으로 심하게 쏠리기 때문이다.
     * 선형으로 두면 상위 한둘이 나머지를 통째로 삼켜 가중치 비율이 의미를 잃는다.
     *
     * <p><b>1로 자르지 않는다</b> — 기준을 넘는 아티스트가 전부 같은 값이 되면 최상위권의
     * 변별이 사라진다. 순위만 쓰므로 범위가 {@code [0,1]} 일 필요가 없다.
     *
     * <p>기준값이 0 이하로 설정되면 분모가 0 이 되어 무한대가 나온다 — 그때는 항을 0 으로
     * 본다. 설정 실수가 <b>순위 전체를 NaN 으로 만드는</b> 것보다 낫다.
     */
    private double normalized(long value, double scale) {
        double denominator = Math.log10(1 + scale);
        if (denominator <= 0) {
            return 0;
        }
        return Math.log10(1 + Math.max(value, 0)) / denominator;
    }

    /**
     * 모멘텀 항 — <b>절대 증감이 아니라 기존 값 대비</b>다.
     *
     * <p>순수 비율은 작은 분모에서 발산한다 — {@code 0 → 3} 이 {@code 1000 → 2000} 을
     * 이긴다. 분모의 {@code K}({@code deltaFloor})가 그 지점을 막는다. IMDb 가중 평점이
     * 표본이 작을 때 전체 평균으로 끌어당기는 것과 같은 축소 추정이다.
     *
     * <p>결과를 {@code [0, 1]} 로 옮긴다 — <b>0.5 가 "변동 없음"</b> 이고 하락은 그보다
     * 낮다. 음수를 그대로 두면 하락 폭이 큰 아티스트가 전체 점수를 음수로 끌어 누적·최근
     * 항을 무의미하게 만든다.
     *
     * <p>패키지 공개다 — 이 항만 떼어 검증하려면 다른 두 항이 섞이지 않아야 하는데,
     * 전체 점수로는 {@code recent} 가 규모 항에도 들어가 분리되지 않는다.
     */
    double momentum(long recent, long previous) {
        double scale = properties.scale().delta();
        if (scale <= 0) {
            return 0.5;
        }
        // K 를 0 으로 설정하면 반응이 없던 아티스트에서 0/0 = NaN 이 나와 정렬이 통째로
        // 무너진다 — 설정 실수를 여기서 닫는다
        double denominator = previous + properties.deltaFloor();
        if (denominator <= 0) {
            return 0.5;
        }
        return 0.5 + 0.5 * Math.clamp((recent - previous) / denominator / scale, -1.0, 1.0);
    }

    /** 아티스트 하나의 반응량 누산기 — 챌린지를 훑는 동안만 산다. */
    private static final class Reaction {
        private long total;
        private long recent;
        private long previous;
    }
}
