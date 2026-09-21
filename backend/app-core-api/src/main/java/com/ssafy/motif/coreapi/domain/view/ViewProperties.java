package com.ssafy.motif.coreapi.domain.view;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 조회수 집계 설정 (API 명세 §7.4 · §8.5 · ADR-0015 · ADR-0016).
 *
 * <p>prefix 가 {@code motif.video-view} 에서 {@code motif.view} 로 바뀌었다 — 챌린지까지
 * 같은 파이프라인을 쓰게 되면서 "video" 가 사실과 달라졌다(ADR-0016).
 *
 * <p><b>왜 설정인가.</b> 여기 수치는 전부 명세에 근거가 없는 기본안이다. 코드에 박으면
 * 조정할 때마다 재배포가 된다. 특히 {@code maxCountPerTarget} 은 <b>지금 유일한 어뷰징
 * 방어선</b>이라(1회 인정 기준이 미결 — 원본 §16 #3) 값을 빨리 조일 수 있어야 한다.
 *
 * <p><b>아티스트 랭킹(§5.1) 설정은 여기 없다.</b> 그쪽은 {@code motif.ranking} 이 계속
 * 소유한다 — 조회수 집계가 아니라 점수 정책이고, 두 랭킹이 서로 다른 가중치를 쓰기
 * 때문이다(ADR-0016).
 *
 * @param maxTargetsPerRequest 한 요청에 담을 수 있는 대상 수. 넘으면 요청 전체가 400 이다
 * @param maxCountPerTarget    대상 하나에 실을 수 있는 조회수. 넘으면 요청 전체가 400 이다
 * @param interval             집계 배치 주기. <b>버킷이 개수 기반이라 이 값만 내리면
 *                             "최근 1시간" 의 실제 길이가 함께 줄어든다</b> — 시연에서
 *                             낮출 때는 {@code recent-buckets} 를 같이 올려야 한다
 * @param bucketTtl            구간 버킷 HASH 의 수명
 * @param bucketKeep           버킷 인덱스 LIST 보존 개수. 여기서 밀려난 버킷은 TTL 이
 *                             지우기 전이라도 조회 대상에서 빠진다
 * @param flushMarkerTtl       "이 구간은 DB 에 넣었다" 는 멱등 마커의 수명. 재처리가 이보다
 *                             늦으면 같은 조회수가 두 번 반영된다 — 넉넉히 둔다
 * @param recentBuckets        "최근 조회수" 로 볼 버킷 <b>개수</b>. 시간이 아니라 개수인
 *                             것은 버킷을 시각으로 계산하지 않기 때문이다(ADR-0015)
 * @param videoRanking         §6.1 HOT 영상 랭킹 — 집계와 같은 틱에 돈다
 */
@ConfigurationProperties(prefix = "motif.view")
public record ViewProperties(int maxTargetsPerRequest, int maxCountPerTarget,
                             Duration interval, Duration bucketTtl, int bucketKeep,
                             Duration flushMarkerTtl, int recentBuckets,
                             Ranking videoRanking) {

    /**
     * §6.1 HOT 영상 랭킹 점수·스냅샷 설정.
     *
     * @param keep             스냅샷에 담을 상위 개수. <b>점수 임계로 자르지 않는다</b> —
     *                         트래픽이 적은 시간대에 목록이 통째로 비는 편이 나쁘다.
     *                         §5.1 에는 이 상한이 없다(전량이 필요하다 — ADR-0016)
     * @param retain           버전 키 TTL. 커서 시퀀스의 유효 기간이자 <b>{@code rankDelta}
     *                         대비 버전의 보존 기간</b>이다. {@code interval ×
     *                         rankDeltaBuckets} 보다 짧으면 순위 변동이 조용히 전원
     *                         {@code null} 이 된다
     * @param rankDeltaBuckets 순위 변동을 몇 틱 전과 견줄지. 1틱이면 변동이 너무 자주
     *                         바뀌어 표시가 깜빡인다
     * @param deltaFloor       증감 분모의 완충 상수 {@code K}. 없으면 {@code 0 → 3} 같은
     *                         노이즈가 {@code 1000 → 2000} 을 이긴다
     * @param weight           세 항의 비율. 합이 1일 필요는 없다 — 상수배는 순위를 바꾸지
     *                         않으므로 비율만 의미가 있다
     * @param scale            정규화 기준값. <b>그 틱의 최대값이 아니다</b> — 최대값으로
     *                         나누면 다른 영상의 등락이 내 점수를 바꿔 {@code rankDelta} 에
     *                         거짓 변동이 뜬다
     */
    public record Ranking(int keep, Duration retain, int rankDeltaBuckets, double deltaFloor,
                          Weight weight, Scale scale) {
    }

    /** @param total 누적 · @param recent 최근 1시간 · @param delta 증감(모멘텀) */
    public record Weight(double total, double recent, double delta) {
    }

    /**
     * 각 항이 {@code 1.0} 이 되는 기준값.
     *
     * @param total  이 누적 조회수를 1.0 으로 본다
     * @param recent 이 시간당 조회수를 1.0 으로 본다
     * @param delta  이 증감율을 최대 가속으로 본다
     */
    public record Scale(double total, double recent, double delta) {
    }
}
