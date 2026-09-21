package com.ssafy.motif.coreapi.domain.video.ranking;

import com.ssafy.motif.coreapi.domain.video.view.VideoViewBuffer;
import com.ssafy.motif.coreapi.domain.video.view.VideoViewProperties;
import com.ssafy.motif.domain.core.video.ParticipationVideoRepository;
import com.ssafy.motif.domain.core.video.VideoScoreRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * HOT 참여 영상 랭킹 스냅샷 배치 (API 명세 §6.1) — §6.1 API 는 이 배치가 만든 것을
 * 읽기만 한다.
 *
 * <p><b>스스로 스케줄되지 않는다</b> *(2026-09-21 — ADR-0015)*. 조회수 집계
 * ({@code VideoViewFlushJob})의 마지막 단계로 불린다. 주기를 따로 두면 <b>버킷이
 * 저장되기 전에 랭킹이 돌아</b> 한 틱 낡은 최근 조회수로 순위를 매기는 구간이 생긴다.
 *
 * <p><b>집계가 실패해도 돈다.</b> 버킷이 그대로여도 구간이 한 칸씩 밀리면 점수가
 * 달라지고, 조회가 0건인 주기에도 순위는 갱신돼야 한다. 그래서 호출부가 집계와 랭킹을
 * 각각 감싼다.
 *
 * <p><b>ShedLock 을 쓰지 않는다.</b> core-api 는 1 레플리카고(ADR-0009) 이 단계는
 * 상태를 누적하지 않는 <b>전량 재계산</b>이라 중복 실행돼도 결과가 같다 — 같은 배치
 * 안의 조회수 집계는 누적 연산이라 사정이 다르다는 점에 주의한다.
 *
 * <p><b>후보를 키 프리픽스로 거르지 않는다.</b> 한때 "공개 사본이 준비된 것만" 남기는
 * 필터가 있었다. 공개 사본 트리가 폐기되면서 그 구간이 사라졌고(ADR-0013), 지금은 참여
 * 영상이 전부 보호 등급이라 <b>되살리면 후보가 전멸해 HOT 이 영구히 빈다</b>.
 *
 * <p><b>예외를 삼킨다.</b> 실패해도 직전 스냅샷과 포인터가 그대로 살아 있다 — 버전 키
 * TTL({@code retain}) 안에 다음 틱이 성공하면 사용자는 아무것도 느끼지 못한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VideoRankingSnapshotJob {

    private final ParticipationVideoRepository participationVideoRepository;
    private final VideoViewBuffer buffer;
    private final VideoRankingScore rankingScore;
    private final VideoRankingSnapshot snapshot;
    private final VideoViewProperties properties;

    /**
     * 스냅샷 갱신.
     *
     * <p>순서를 <b>여기서 확정</b>한다({@code score DESC → videoSeq ASC}). 동점 처리를
     * Redis 에 맡기지 않는 이유는 {@link VideoRankingSnapshot} javadoc 에 있다.
     * 동점 보조 키가 {@code videoSeq ASC} 인 것은 같은 점수일 때 먼저 게시된 영상을
     * 앞에 두려는 것이다 — PK 가 IDENTITY 라 단조 증가한다.
     *
     * <p><b>상위 {@code keep} 개만 담는다.</b> 점수 임계로 자르지 않는 것은 트래픽이
     * 적은 시간대에 목록이 통째로 비기 때문이다 — 개수 상한은 결과가 예측된다.
     */
    public void refresh() {
        try {
            List<VideoScoreRow> candidates = participationVideoRepository.findRankingInputs();

            if (candidates.isEmpty()) {
                snapshot.clear();
                log.info("HOT 랭킹 후보가 0건이다 — 스냅샷 포인터를 지웠다");
                return;
            }

            int window = properties.recentBuckets();
            Map<Long, Long> recent = buffer.sumBuckets(0, window);
            Map<Long, Long> previous = buffer.sumBuckets(window, window);

            Map<Long, Double> scores = rankingScore.compute(candidates, recent, previous);
            List<Long> ordered = scores.entrySet().stream()
                    .sorted(Map.Entry.<Long, Double>comparingByValue().reversed()
                            .thenComparing(Map.Entry::getKey))
                    .map(Map.Entry::getKey)
                    .limit(properties.ranking().keep())
                    .toList();

            long version = snapshot.replace(ordered);
            log.info("HOT 랭킹 스냅샷 갱신 — version={} 후보 {}건 중 {}건 적재", version,
                    candidates.size(), ordered.size());
        } catch (Exception e) {
            log.error("HOT 랭킹 스냅샷 갱신 실패 — 직전 스냅샷을 유지한다", e);
        }
    }

}
