package com.ssafy.motif.coreapi.domain.artist.ranking;

import com.ssafy.motif.domain.core.challenge.ChallengeScoreRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * 아티스트 랭킹 점수 공식 (API 명세 §5.1) — <b>순수 계산</b>.
 *
 * <pre>
 * score_artist = Σ_c ( view_count_c × Wv + participant_count_c × Wp ) × 0.5 ^ (age_c / H)
 * </pre>
 *
 * <p><b>점수의 출처는 챌린지가 받은 반응뿐이다.</b> 챌린지를 올리는 행위 자체에는 점수를
 * 주지 않는다 — 주면 반응이 하나도 없는 챌린지를 여러 개 올리는 것만으로 순위가 올라
 * 인기 랭킹이 아니라 활동량 랭킹이 된다.
 *
 * <p><b>감쇠는 챌린지 하나하나에 걸린다.</b> 아티스트 단위로 합쳐 놓고 나중에 깎으면
 * 옛 챌린지와 새 챌린지가 같은 계수를 받는다. 조회 10,000회를 모은 30일 된 챌린지
 * (× 0.2264 = 2,264)가 조회 3,000회짜리 2일 된 챌린지(× 0.9057 = 2,717)에 밀리는 것이
 * 이 공식의 요점이다.
 *
 * <p><b>DB 가 아니라 여기서 계산하는 이유.</b> {@code power()}·{@code extract(epoch …)} 로
 * SQL 에서도 되지만, 그러면 공식이 쿼리 문자열 안에 갇혀 단위 테스트로 묶이지 않는다.
 * 가중치·반감기를 설정으로 빼 놓고 정작 그 계산을 검증할 수 없게 되는 것이 더 나쁘다.
 *
 * <p>기준 시각을 파라미터로 받는다 — {@code Instant.now()} 를 안에서 부르면 테스트가
 * 시간을 고정할 수 없다.
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
     * @param now        감쇠 기준 시각
     */
    public Map<Integer, Double> compute(Collection<Integer> artistSeqs,
                                        Collection<ChallengeScoreRow> challenges,
                                        Instant now) {
        Map<Integer, Double> scores = new HashMap<>();
        artistSeqs.forEach(artistSeq -> scores.put(artistSeq, 0.0));

        for (ChallengeScoreRow challenge : challenges) {
            // 스냅샷에 없는 아티스트(삭제됨)의 챌린지는 버린다 — merge 로 되살리면
            // 메타 조회에서 이름이 안 나오는 유령 항목이 랭킹에 뜬다
            scores.computeIfPresent(challenge.artistSeq(),
                    (artistSeq, score) -> score + of(challenge, now));
        }
        return scores;
    }

    /** 챌린지 한 건의 기여분 — 반응 가중합에 나이 감쇠를 곱한다. */
    public double of(ChallengeScoreRow challenge, Instant now) {
        double reaction = challenge.viewCount() * properties.weight().view()
                + challenge.participantCount() * properties.weight().participant();
        return reaction * decay(challenge.regDttm(), now);
    }

    /**
     * 나이 감쇠 계수 — 반감기 {@code H} 마다 절반이 된다.
     *
     * <p>미래 시각({@code regDttm > now})은 1 로 자른다. 시계 차이로 계수가 1 을 넘으면
     * 갓 등록된 챌린지가 반응 대비 과대평가된다.
     */
    private double decay(Instant regDttm, Instant now) {
        double halfLifeSeconds = properties.halfLife().toSeconds();
        double ageSeconds = Duration.between(regDttm, now).toSeconds();
        if (ageSeconds <= 0) {
            return 1.0;
        }
        return Math.pow(0.5, ageSeconds / halfLifeSeconds);
    }
}
