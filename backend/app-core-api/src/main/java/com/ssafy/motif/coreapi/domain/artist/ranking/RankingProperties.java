package com.ssafy.motif.coreapi.domain.artist.ranking;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 아티스트 랭킹 점수·주기 설정 (API 명세 §5.1).
 *
 * <p><b>왜 설정인가.</b> 원본 명세는 §5.1 의 API 계약만 적고 점수 공식과 갱신 주기를
 * 정하지 않았다. 여기 수치는 전부 S15P21D104-51 에서 정한 <b>기본안</b>이라 근거가
 * 명세에 없다 — 코드에 박으면 조정할 때마다 재배포가 된다.
 *
 * <p>주기를 {@code fixedDelayString} 으로 읽으므로 시연 프로파일에서 30초 / 1분으로
 * 낮춰 순위 변동을 보여줄 수 있다.
 *
 * @param weight      점수 가중치
 * @param halfLife    감쇠 반감기 — 이만큼 지난 챌린지의 반응은 절반으로 친다
 * @param interval    {@code rank:artist:current} 갱신 주기. §1.6 이 클라 캐시 TTL 5분을
 *                    "랭킹 배치 주기와 동일"로 묶어 놨다
 * @param prevInterval {@code rank:artist:prev} 갱신 주기 — {@code rankDelta} 의 대비 구간이다.
 *                    {@code interval} 과 같게 두면 대비 구간이 한 틱이라 변동이 거의 잡히지
 *                    않는다
 */
@ConfigurationProperties(prefix = "motif.ranking")
public record RankingProperties(
        Weight weight, Duration halfLife, Duration interval, Duration prevInterval) {

    /**
     * @param view        조회 1회의 점수
     * @param participant 참여 1건의 점수. 참여는 촬영·업로드를 요구하는 행위라 단순 조회보다
     *                    무겁다
     */
    public record Weight(double view, double participant) {
    }
}
