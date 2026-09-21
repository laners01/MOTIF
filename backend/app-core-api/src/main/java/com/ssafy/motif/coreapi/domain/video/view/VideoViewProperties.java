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
 */
@ConfigurationProperties(prefix = "motif.video-view")
public record VideoViewProperties(int maxVideosPerRequest, int maxCountPerVideo,
                                  Duration interval, Duration bucketTtl, int bucketKeep,
                                  Duration flushMarkerTtl) {
}
