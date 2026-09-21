package com.ssafy.motif.coreapi.domain.artist.ranking;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 아티스트 랭킹 점수·스냅샷 설정 (API 명세 §5.1 · ADR-0016).
 *
 * <p><b>왜 설정인가.</b> 원본 명세는 §5.1 의 API 계약만 적고 점수 공식을 정하지 않았다.
 * 여기 수치는 전부 기본안이라 근거가 명세에 없다 — 코드에 박으면 조정할 때마다 재배포가
 * 된다. 특히 {@code scale} 은 <b>실트래픽 없이 자릿수만 맞춘 추정값</b>이라, 배치가
 * 남기는 항별 점수 로그를 보고 조정할 것을 전제로 한다.
 *
 * <p><b>주기 설정이 없다.</b> 랭킹은 조회수 집계 배치({@code motif.view.interval})의
 * 마지막 단계로 돌기 때문이다 — {@code interval}·{@code prev-interval} 은 폐기했다.
 *
 * <p><b>{@code half-life} 도 없다.</b> 시간 감쇠를 걷어냈다(ADR-0016) — 남기면 오래된
 * 챌린지가 지금 아무리 돌아도 올라오지 못한다.
 *
 * @param reaction         반응 환산비. 조회와 참여를 한 축으로 합칠 때 쓴다
 * @param weight           세 항의 비율. <b>§6.1 과 다르다</b> — 이 스냅샷을 §3.1 온보딩
 *                         인기 목록이 함께 쓰므로 누적 비중이 크다(ADR-0016)
 * @param scale            각 항이 1.0 이 되는 기준값. <b>그 틱의 최대값이 아니다</b> —
 *                         최대값으로 나누면 다른 아티스트의 등락이 내 점수를 바꿔
 *                         {@code rankDelta} 에 거짓 변동이 뜬다
 * @param deltaFloor       증감 분모의 완충 상수 {@code K}. 없으면 작은 분모에서 비율이
 *                         발산해 노이즈가 1위를 가져간다. <b>§6.1(10)보다 크다</b> —
 *                         아티스트 반응량은 챌린지 합이라 자릿수가 다르다
 * @param retain           버전 키 TTL. §5.1 은 커서가 없으므로 이 값의 역할은
 *                         <b>{@code rankDelta} 대비 버전의 보존</b>이다. 집계 주기 ×
 *                         {@code rankDeltaBuckets} 보다 짧으면 순위 변동이 조용히 전원
 *                         {@code null} 이 된다
 * @param rankDeltaBuckets 순위 변동을 몇 틱 전과 견줄지. 1틱이면 20분마다 값이 바뀌어
 *                         앱의 변동 표시가 깜빡인다
 */
@ConfigurationProperties(prefix = "motif.ranking")
public record RankingProperties(Reaction reaction, Weight weight, Scale scale,
                                double deltaFloor, Duration retain, int rankDeltaBuckets) {

    /**
     * @param view        조회 1회의 반응량
     * @param participant 참여 영상 1건의 반응량. 참여는 촬영·업로드를 요구하는 행위라
     *                    단순 조회보다 무겁다. <b>누적 항에만 곱해진다</b> — 1시간 창에서는
     *                    참여가 0~2건이라 이 환산비가 on/off 스위치가 된다(ADR-0016)
     */
    public record Reaction(double view, double participant) {
    }

    /** @param total 누적 · @param recent 최근 1시간 · @param delta 증감(모멘텀) */
    public record Weight(double total, double recent, double delta) {
    }

    /**
     * @param total  이 누적 반응량을 1.0 으로 본다
     * @param recent 이 시간당 조회수를 1.0 으로 본다
     * @param delta  이 증감율을 최대 가속으로 본다
     */
    public record Scale(double total, double recent, double delta) {
    }
}
