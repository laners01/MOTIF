package com.ssafy.motif.coreapi.domain.video.view;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 조회수 집계 설정 (API 명세 §8.5 · ADR-0015).
 *
 * <p>prefix 를 {@code motif.hot-video} 아래에 두지 않는다 — 그쪽은 §6.1 랭킹
 * ({@code VideoRankingProperties}) 것이고, 병렬 브랜치가 같은 파일에 칸을 더하면
 * 리베이스에서 되돌아온다({@code VideoRankingProperties} 가 {@code motif.ranking} 을
 * 피한 것과 같은 이유).
 *
 * <p><b>왜 설정인가.</b> 두 상한 모두 명세에 근거가 없는 S15P21D104-130 기본안이다.
 * 코드에 박으면 조정할 때마다 재배포가 된다. 특히 {@code maxCountPerVideo} 는
 * <b>지금 유일한 어뷰징 방어선</b>이라(1회 인정 기준이 미결 — 원본 §16 #3) 값을 빨리
 * 조일 수 있어야 한다.
 *
 * @param maxVideosPerRequest 한 요청에 담을 수 있는 영상 수. 넘으면 요청 전체가 400 이다
 * @param maxCountPerVideo    영상 하나에 실을 수 있는 조회수. 넘으면 요청 전체가 400 이다
 * @param interval            집계 배치 주기. <b>버킷이 개수 기반이라 이 값만 내리면
 *                            "최근 1시간" 의 실제 길이가 함께 줄어든다</b> — 시연에서
 *                            낮출 때는 {@code recent-buckets} 를 같이 올려야 한다
 * @param bucketTtl           20분 버킷 HASH 의 수명. 최근·이전 구간을 덮고도 재처리·관찰
 *                            여유가 남을 만큼
 * @param bucketKeep          버킷 인덱스 LIST 보존 개수. 여기서 밀려난 버킷은 TTL 이
 *                            지우기 전이라도 조회 대상에서 빠진다
 * @param flushMarkerTtl      "이 구간은 DB 에 넣었다" 는 멱등 마커의 수명. 재처리가 이보다
 *                            늦으면 같은 조회수가 두 번 반영된다 — 넉넉히 둔다
 * @param recentBuckets       "최근 조회수" 로 볼 버킷 <b>개수</b>. 시간이 아니라 개수인
 *                            것은 버킷을 시각으로 계산하지 않기 때문이다(ADR-0015) —
 *                            {@code interval} 을 곱한 값이 실제 구간 길이다
 * @param ranking             §6.1 HOT 랭킹 — 집계와 같은 틱에 돈다
 */
@ConfigurationProperties(prefix = "motif.video-view")
public record VideoViewProperties(int maxVideosPerRequest, int maxCountPerVideo,
                                  Duration interval, Duration bucketTtl, int bucketKeep,
                                  Duration flushMarkerTtl, int recentBuckets,
                                  Ranking ranking) {

    /**
     * 랭킹 점수·스냅샷 설정 (API 명세 §6.1 · ADR-0015).
     *
     * <p><b>왜 {@code motif.hot-video} 가 아닌가.</b> 랭킹이 조회수 집계 배치의 마지막
     * 단계가 되면서 두 설정의 수명이 같아졌다 — {@code interval} 을 시연용으로 내리면
     * 랭킹 주기도 함께 내려간다. 프리픽스가 갈려 있으면 한쪽만 고치고 왜 안 바뀌는지
     * 찾게 된다.
     *
     * @param keep             스냅샷에 담을 상위 개수. <b>점수 임계로 자르지 않는다</b> —
     *                         트래픽이 적은 시간대에 목록이 통째로 비는 편이 나쁘다
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
