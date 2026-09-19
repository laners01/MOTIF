package com.ssafy.motif.coreapi.domain.video.ranking;

import com.ssafy.motif.domain.core.video.VideoScoreRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * HOT 참여 영상 점수 공식 (API 명세 §6.1) — <b>순수 계산</b>.
 *
 * <pre>
 * score_video = view_count × 0.5 ^ (age / H)
 * </pre>
 *
 * <p><b>조회수와 게시 후 경과 시간만 쓴다.</b> 좋아요는 넣지 않는다(§6.1 랭킹 기본안).
 *
 * <p><b>나이의 기준은 게시 시각이다.</b> {@code view_count} 가 누적 카운터라 개별 조회의
 * 발생 시각이 DB 에 없어, 걸 수 있는 시간축이 {@code participation_videos.reg_dttm}
 * 하나뿐이다. 그래서 감쇠는 "이 영상이 얼마나 오래된 게시물인가" 이지 "조회가 언제
 * 있었나" 가 아니다.
 *
 * <p><b>DB 가 아니라 여기서 계산하는 이유.</b> {@code power()}·{@code extract(epoch …)} 로
 * SQL 에서도 되지만, 그러면 공식이 쿼리 문자열 안에 갇혀 단위 테스트로 묶이지 않는다.
 * 반감기를 설정으로 빼 놓고 정작 그 계산을 검증할 수 없게 되는 것이 더 나쁘다
 * (§5.1 {@code RankingScore} 와 같은 판단).
 *
 * <p>기준 시각을 파라미터로 받는다 — {@code Instant.now()} 를 안에서 부르면 테스트가
 * 시간을 고정할 수 없다.
 */
@Component
@RequiredArgsConstructor
public class VideoRankingScore {

    private final VideoRankingProperties properties;

    /**
     * 영상별 점수를 낸다.
     *
     * <p><b>조회수 0인 영상도 결과에 들어간다</b>(0점). 스냅샷이 곧 노출 범위라
     * 여기서 빼면 아직 아무도 안 본 영상이 HOT 에 영원히 못 오른다 — 조회가 생겨도
     * 그때 스냅샷에 없으면 순위를 받을 수 없다. 0점은 목록 맨 뒤라 해가 없다.
     *
     * @param rows 배치가 §1.8 비노출까지 걸러 넘긴 후보
     * @param now  감쇠 기준 시각
     */
    public Map<Long, Double> compute(Collection<VideoScoreRow> rows, Instant now) {
        Map<Long, Double> scores = new HashMap<>();
        for (VideoScoreRow row : rows) {
            scores.put(row.videoSeq(), of(row, now));
        }
        return scores;
    }

    /** 영상 한 건의 점수 — 조회수에 나이 감쇠를 곱한다. */
    public double of(VideoScoreRow row, Instant now) {
        return row.viewCount() * decay(row.regDttm(), now);
    }

    /**
     * 나이 감쇠 계수 — 반감기 {@code H} 마다 절반이 된다.
     *
     * <p><b>미래 시각({@code regDttm > now})은 1 로 자른다.</b> 자르지 않으면 지수가
     * 음수가 되어 계수가 1을 넘고, 앞선 정도가 반감기와 같으면 조회수를 2배로 센다.
     * 시계 차이로 이 일이 생기면 <b>예외도 로그도 없이 순위만 어긋나</b> 원인을 찾을
     * 근거가 남지 않는다. {@code age → 0} 의 극한값이 1.0 이므로 이 분기가 값을 바꾸는
     * 것은 음수 나이뿐이다.
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
