package com.ssafy.motif.coreapi.domain.video.ranking;

import com.ssafy.motif.coreapi.domain.media.MediaKeys;
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
            List<VideoScoreRow> candidates = publicCopyReady(
                    participationVideoRepository.findRankingInputs());

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

    /**
     * 공개 사본이 준비된 것만 남긴다.
     *
     * <p>§1.7 의 복사 규약 때문에 {@code visibility=PUBLIC} 인데 {@code video_key} 가
     * 아직 보호 프리픽스인 구간이 존재한다. 그 키로 무서명 CDN URL 을 만들면 403 이고,
     * 보호 등급 재생목록 주소로 내보내도 그쪽 인가가 소유자 전용이라 <b>남의 영상은
     * 404</b> 다 — HOT 은 정의상 남의 영상이다. 그래서 스냅샷에 올리지 않고 복사가
     * 끝난 다음 틱에 들어오게 한다.
     *
     * <p>판정은 {@link MediaKeys#isProtected} 로 한다. SQL 에 {@code 'private/%'} 를
     * 넣으면 등급 규약이 두 곳으로 갈라진다.
     */
    private List<VideoScoreRow> publicCopyReady(List<VideoScoreRow> rows) {
        List<VideoScoreRow> ready = rows.stream()
                .filter(row -> !MediaKeys.isProtected(row.videoKey()))
                .toList();

        int skipped = rows.size() - ready.size();
        if (skipped > 0) {
            // 정상적으로도 게시 직후 잠깐 생긴다. 계속 늘어나면 복사가 안 되고 있다는 뜻
            log.info("HOT 랭킹 — 공개 사본이 없어 제외한 PUBLIC 영상 {}건", skipped);
        }
        return ready;
    }
}
