package com.ssafy.motif.coreapi.domain.video.ranking;

import com.ssafy.motif.domain.core.video.ParticipationVideoRepository;
import com.ssafy.motif.domain.core.video.VideoScoreRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * HOT 참여 영상 랭킹 스냅샷 배치 (API 명세 §6.1) — §6.1 API 는 이 배치가 만든 것을
 * 읽기만 한다.
 *
 * <p><b>주기가 하나다.</b> §5.1 은 {@code rankDelta} 를 내려야 해서 대비용
 * {@code :prev} 를 더 긴 주기로 함께 굴리지만, §6.1 응답에는 순위 변동 칸이 없다.
 *
 * <p><b>ShedLock 을 쓰지 않는다.</b> core-api 는 1 레플리카고(ADR-0009) 이 배치는
 * 상태를 누적하지 않는 <b>전량 재계산</b>이라 중복 실행돼도 결과가 같다. 다중화되면
 * 어드민 §3.16.4(배치 수동 실행 + 락 409) 티켓에서 함께 넣는다.
 *
 * <p><b>후보를 키 프리픽스로 거르지 않는다.</b> 한때 "공개 사본이 준비된 것만" 남기는
 * 필터가 있었다 — {@code visibility=PUBLIC} 인데 키가 아직 {@code private/} 인 구간의
 * 영상은 남이 재생할 수 없다고 봤기 때문이다. 공개 사본 트리가 폐기되면서 그 구간이
 * 사라졌고(ADR-0013), 지금은 참여 영상이 전부 보호 등급이라 <b>되살리면 후보가
 * 전멸해 HOT 이 영구히 빈다</b>. 남의 {@code PUBLIC} 영상도
 * {@code MediaPlaylistQueryService} 가 소유자 검사 없이 통과시켜 정상 재생된다.
 *
 * <p><b>예외를 삼킨다.</b> 스케줄된 작업이 예외를 던지면 로그만 남고 다음 주기에 다시
 * 돈다 — 스케줄 자체는 죽지 않는다. 그래도 여기서 잡아 로그를 남기는 것은 DB·Redis
 * 중 어느 쪽에서 실패했는지가 스택만으로는 잘 안 보이기 때문이다. 실패해도 직전
 * 스냅샷과 포인터가 그대로 살아 있다 — 버전 키 TTL({@code retain}) 안에 다음 틱이
 * 성공하면 사용자는 아무것도 느끼지 못한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VideoRankingSnapshotJob {

    private final ParticipationVideoRepository participationVideoRepository;
    private final VideoRankingScore rankingScore;
    private final VideoRankingSnapshot snapshot;

    /**
     * 스냅샷 갱신 — 기본 5분.
     *
     * <p>순서를 <b>여기서 확정</b>한다({@code score DESC → videoSeq ASC}). 동점 처리를
     * Redis 에 맡기지 않는 이유는 {@link VideoRankingSnapshot} javadoc 에 있다.
     * 동점 보조 키가 {@code videoSeq ASC} 인 것은 같은 점수일 때 먼저 게시된 영상을
     * 앞에 두려는 것이다 — PK 가 IDENTITY 라 단조 증가한다.
     */
    public void refresh() {
        try {
            List<VideoScoreRow> candidates = participationVideoRepository.findRankingInputs();

            if (candidates.isEmpty()) {
                snapshot.clear();
                log.info("HOT 랭킹 후보가 0건이다 — 스냅샷 포인터를 지웠다");
                return;
            }

            Map<Long, Double> scores = rankingScore.compute(candidates, Instant.now());
            List<Long> ordered = scores.entrySet().stream()
                    .sorted(Map.Entry.<Long, Double>comparingByValue().reversed()
                            .thenComparing(Map.Entry::getKey))
                    .map(Map.Entry::getKey)
                    .toList();

            long version = snapshot.replace(ordered);
            log.info("HOT 랭킹 스냅샷 갱신 — version={} {}건", version, ordered.size());
        } catch (Exception e) {
            log.error("HOT 랭킹 스냅샷 갱신 실패 — 직전 스냅샷을 유지한다", e);
        }
    }

}
