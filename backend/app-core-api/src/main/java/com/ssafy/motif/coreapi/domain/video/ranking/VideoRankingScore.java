package com.ssafy.motif.coreapi.domain.video.ranking;

import com.ssafy.motif.coreapi.domain.video.view.VideoViewProperties;
import com.ssafy.motif.domain.core.video.VideoScoreRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * HOT 참여 영상 점수 공식 (API 명세 §6.1 · ADR-0015) — <b>순수 계산</b>.
 *
 * <pre>
 * tScore = log10(1 + totalViews)    / log10(1 + T0)
 * rScore = log10(1 + recentViews1h) / log10(1 + R0)
 * dRel   = (recentViews1h − previousViews1h) / (previousViews1h + K)
 * dScore = 0.5 + 0.5 × clamp(dRel / D0, −1, +1)
 *
 * score  = Wt·tScore + Wr·rScore + Wd·dScore
 * </pre>
 *
 * <p><b>세 항이 서로 다른 것을 잰다</b> — 누적 규모(절대) · 최근 규모(절대) · 모멘텀(상대).
 * 절대 증감을 따로 넣지 않는 것은 {@code recentViews1h} 가 이미 최근의 절대량이라 같은
 * 신호가 두 번 가산되기 때문이다.
 *
 * <p><b>시간 감쇠가 없다</b> *(2026-09-21 — ADR-0015)*. 예전 공식은
 * {@code view_count × 0.5^(age/H)} 였고 나이의 기준이 게시 시각이었다 — {@code view_count}
 * 가 누적 카운터라 걸 수 있는 시간축이 그것뿐이었기 때문이다. 버킷이 "지금 얼마나 보고
 * 있는가" 를 직접 주면서 감쇠가 필요 없어졌고, 무엇보다 <b>감쇠를 남기면 오래된 영상이
 * 지금 아무리 떠도 계수에 눌려 올라올 수 없다</b>. 되살리지 말 것.
 *
 * <p><b>정규화 기준이 그 틱의 최대값이 아니라 고정 상수다.</b> 최대값으로 나누면 ① 다른
 * 영상이 떡상했다는 이유만으로 내 점수가 내려가고 ② 1위 영상이 삭제되면 전원의 점수가
 * 움직이며 ③ 같은 입력이 다른 순위를 낳아 나중에 재현할 수 없다. 앞의 둘은
 * {@code rankDelta} 를 응답에 싣는 순간 <b>아무도 아무 것도 하지 않았는데 뜨는 거짓
 * 변동</b>이 된다.
 *
 * <p><b>{@code tScore}·{@code rScore} 를 1 로 자르지 않는다.</b> 기준을 넘는 영상이 전부
 * 같은 값이 되면 최상위권의 변별이 사라진다 — 순위만 쓰므로 범위가 {@code [0,1]} 일
 * 필요가 없다. {@code dScore} 만 자르는 것은 분모가 작을 때 발산하기 때문이다.
 *
 * <p><b>DB 가 아니라 여기서 계산하는 이유.</b> {@code log()} 로 SQL 에서도 되지만 그러면
 * 공식이 쿼리 문자열 안에 갇혀 단위 테스트로 묶이지 않는다. 가중치를 설정으로 빼 놓고
 * 정작 그 계산을 검증할 수 없게 되는 것이 더 나쁘다(§5.1 {@code RankingScore} 와 같은
 * 판단). 게다가 최근·이전 조회수는 DB 에 아예 없다.
 */
@Component
@RequiredArgsConstructor
public class VideoRankingScore {

    private final VideoViewProperties properties;

    /**
     * 영상별 점수를 낸다.
     *
     * <p><b>버킷에 없는 영상도 결과에 들어간다.</b> 최근·이전이 0 이면 그 두 항이 낮게
     * 나올 뿐 후보에서 빠지지 않는다 — 여기서 빼면 한 주기 조용했다는 이유로 목록에서
     * 사라졌다 다시 나타나 순위가 매 틱 요동친다.
     *
     * @param rows     배치가 §1.8 비노출까지 걸러 넘긴 후보 ({@code view_count > 0})
     * @param recent   최근 구간 조회수 — {@code videoSeq → 합}
     * @param previous 그 이전 구간 조회수
     */
    public Map<Long, Double> compute(Collection<VideoScoreRow> rows,
                                     Map<Long, Long> recent,
                                     Map<Long, Long> previous) {
        Map<Long, Double> scores = new HashMap<>();
        for (VideoScoreRow row : rows) {
            long recentViews = recent.getOrDefault(row.videoSeq(), 0L);
            long previousViews = previous.getOrDefault(row.videoSeq(), 0L);
            scores.put(row.videoSeq(), of(row.viewCount(), recentViews, previousViews));
        }
        return scores;
    }

    /** 영상 한 건의 점수 — 세 항의 가중합. */
    public double of(long totalViews, long recentViews, long previousViews) {
        VideoViewProperties.Weight weight = properties.ranking().weight();
        return weight.total() * normalized(totalViews, properties.ranking().scale().total())
                + weight.recent() * normalized(recentViews, properties.ranking().scale().recent())
                + weight.delta() * momentum(recentViews, previousViews);
    }

    /**
     * 규모 항 — {@code log10} 으로 눌러 기준값과 견준다.
     *
     * <p>{@code log} 를 씌우는 것은 조회수의 분포가 한쪽으로 심하게 쏠리기 때문이다.
     * 선형으로 두면 상위 한둘이 나머지를 통째로 삼켜 30/50/20 이라는 비율이 의미를 잃는다.
     *
     * <p>기준값이 0 이하로 설정되면 분모가 0 이 되어 무한대가 나온다 — 그때는 항을 0 으로
     * 본다. 설정 실수가 <b>순위 전체를 NaN 으로 만드는</b> 것보다 낫다.
     */
    private double normalized(long views, double scale) {
        double denominator = Math.log10(1 + scale);
        if (denominator <= 0) {
            return 0;
        }
        return Math.log10(1 + Math.max(views, 0)) / denominator;
    }

    /**
     * 모멘텀 항 — <b>절대 증감이 아니라 기존 값 대비</b>다.
     *
     * <p>{@code +100} 이 직전 50 위에서 난 것과 5,000 위에서 난 것은 다른 사건이다. 다만
     * 순수 비율({@code recent/previous})은 <b>작은 분모에서 폭발한다</b> — {@code 0 → 3} 이
     * {@code 1000 → 2000} 을 이긴다. 분모의 {@code K}({@code deltaFloor})가 그 지점을
     * 막는다: {@code K = 10} 이면 {@code 0 → 3} 의 {@code dRel} 이 0.3 에 그친다.
     *
     * <p>결과를 {@code [0, 1]} 로 옮긴다 — <b>0.5 가 "변동 없음"</b> 이고 하락은 그보다
     * 낮다. 음수를 그대로 두면 하락 폭이 큰 영상이 가중치에 따라 전체 점수를 음수로 끌어
     * 누적·최근 항을 무의미하게 만든다.
     *
     * <p>패키지 공개다 — 이 항만 떼어 검증하려면 다른 두 항이 섞이지 않아야 하는데,
     * 전체 점수로는 {@code recentViews} 가 규모 항에도 들어가 분리되지 않는다.
     */
    double momentum(long recentViews, long previousViews) {
        double scale = properties.ranking().scale().delta();
        if (scale <= 0) {
            return 0.5;
        }
        // K 를 0 으로 설정하면 조회가 없던 영상에서 0/0 = NaN 이 나와 정렬이 통째로
        // 무너진다 — 설정 실수를 여기서 닫는다
        double denominator = previousViews + properties.ranking().deltaFloor();
        if (denominator <= 0) {
            return 0.5;
        }
        double relative = (recentViews - previousViews) / denominator;
        return 0.5 + 0.5 * Math.clamp(relative / scale, -1.0, 1.0);
    }
}
