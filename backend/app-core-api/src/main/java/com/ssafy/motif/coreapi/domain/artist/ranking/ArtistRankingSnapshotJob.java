package com.ssafy.motif.coreapi.domain.artist.ranking;

import com.ssafy.motif.coreapi.domain.view.ViewBuffer;
import com.ssafy.motif.coreapi.domain.view.ViewProperties;
import com.ssafy.motif.domain.core.artist.ArtistRepository;
import com.ssafy.motif.domain.core.challenge.ChallengeRepository;
import com.ssafy.motif.domain.core.challenge.ChallengeScoreRow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 아티스트 랭킹 스냅샷 배치 (API 명세 §5.1) — §5.1 API 는 이 배치가 만든 것을 읽기만 한다.
 *
 * <p><b>스스로 스케줄되지 않는다</b> *(2026-09-21 — ADR-0016)*. 조회수 집계
 * ({@code ViewFlushJob})의 마지막 단계로 불린다. 주기를 따로 두면 <b>챌린지 버킷이
 * 저장되기 전에 랭킹이 돌아</b> 한 틱 낡은 최근 조회수로 순위를 매기는 구간이 생긴다.
 * 예전의 {@code refreshCurrent}/{@code refreshPrevious} 두 주기는 폐기했다 —
 * {@code rankDelta} 는 이제 버전 이력에서 나온다.
 *
 * <p><b>ShedLock 을 쓰지 않는다.</b> core-api 는 1 레플리카고(ADR-0009) 이 단계는 상태를
 * 누적하지 않는 <b>전량 재계산</b>이라 중복 실행돼도 결과가 같다 — 같은 배치 안의 조회수
 * 집계는 누적 연산이라 사정이 다르다는 점에 주의한다.
 *
 * <p><b>예외를 삼킨다.</b> 실패해도 직전 스냅샷과 포인터가 그대로 살아 있다 — 버전 키
 * TTL({@code retain}) 안에 다음 틱이 성공하면 사용자는 아무것도 느끼지 못한다.
 */
@Slf4j
@Component
public class ArtistRankingSnapshotJob {

    private final ArtistRepository artistRepository;
    private final ChallengeRepository challengeRepository;
    private final ViewBuffer challengeViewBuffer;
    private final RankingScore rankingScore;
    private final ArtistRankingSnapshot snapshot;
    private final ViewProperties viewProperties;

    /**
     * <b>생성자를 직접 쓴다.</b> 대상마다 {@link ViewBuffer} 빈이 하나씩이라 한정자가
     * 필요한데, {@code lombok.config} 가 없어 {@code @RequiredArgsConstructor} 는 필드의
     * {@code @Qualifier} 를 생성자로 복사하지 않는다.
     *
     * <p><b>챌린지 버퍼를 받아야 한다.</b> §5.1 의 최근 조회수는 챌린지 버킷에서 온다 —
     * 영상 버퍼를 꽂으면 엉뚱한 구간을 읽는데 타입이 같아 컴파일도 기동도 통과한다.
     */
    public ArtistRankingSnapshotJob(ArtistRepository artistRepository,
                                    ChallengeRepository challengeRepository,
                                    @Qualifier("challengeViewBuffer") ViewBuffer challengeViewBuffer,
                                    RankingScore rankingScore,
                                    ArtistRankingSnapshot snapshot,
                                    ViewProperties viewProperties) {
        this.artistRepository = artistRepository;
        this.challengeRepository = challengeRepository;
        this.challengeViewBuffer = challengeViewBuffer;
        this.rankingScore = rankingScore;
        this.snapshot = snapshot;
        this.viewProperties = viewProperties;
    }

    /**
     * 스냅샷 갱신.
     *
     * <p><b>모집단을 좁히지 않는다.</b> §6.1 은 {@code view_count > 0} 으로 후보를 줄였지만
     * 여기서 같은 것을 하면 <b>온보딩 인기 목록(§3.1)과 {@code UNSUBSCRIBED} 필터에서
     * 신인이 통째로 사라진다</b>. 비삭제 아티스트는 챌린지가 0개여도 0점으로 넣는다.
     *
     * <p><b>상한도 두지 않는다.</b> §5.1 은 필터링과 {@code totalElements} 때문에 전량이
     * 필요하다.
     */
    public void refresh() {
        try {
            Map<Integer, Double> scores = computeScores();
            Long version = snapshot.replace(scores);

            if (version == null) {
                log.info("아티스트 랭킹 후보가 0건이다 — 스냅샷 포인터를 지웠다");
                return;
            }
            log.info("아티스트 랭킹 스냅샷 갱신 — version={} {}명", version, scores.size());
            logTop(scores);
        } catch (Exception e) {
            log.error("아티스트 랭킹 스냅샷 갱신 실패 — 직전 스냅샷을 유지한다", e);
        }
    }

    /**
     * 점수만 낸다 — Redis 를 건드리지 않는다.
     *
     * <p>모집단·합산 규칙을 스냅샷 적재 없이 확인할 수 있는 자리다.
     *
     * <p><b>트랜잭션으로 묶지 않는다.</b> 두 조회 사이에 아티스트가 삭제되면 주인 없는
     * 챌린지가 남는데, 그건 {@link RankingScore#compute} 가 이미 버린다. 묶어 봐야 다음
     * 주기에 어차피 다시 계산되는 값이고, {@code refresh()} 가 이 메서드를 직접 부르면
     * 프록시를 타지 않아 {@code @Transactional} 이 조용히 무시된다 — 붙어 있는데 안 도는
     * 어노테이션이 없는 것보다 나쁘다.
     */
    public Map<Integer, Double> computeScores() {
        List<Integer> artistSeqs = artistRepository.findRankableSeqs();
        List<ChallengeScoreRow> challenges = challengeRepository.findRankingInputs();

        int window = viewProperties.recentBuckets();
        Map<Long, Long> recent = challengeViewBuffer.sumBuckets(0, window);
        Map<Long, Long> previous = challengeViewBuffer.sumBuckets(window, window);

        return rankingScore.compute(artistSeqs, challenges, recent, previous);
    }

    /**
     * 상위 10명의 점수를 남긴다.
     *
     * <p><b>튜닝 근거를 만드는 로그다.</b> {@code scale}·{@code weight} 는 실트래픽 없이
     * 자릿수만 맞춘 추정값이라(ADR-0016), 하루치 실측을 보고 조정할 것을 전제로 한다.
     * 점수가 고정 기준 정규화라 <b>절대값이고 재현된다</b> — 상대 정규화를 버린 실익이
     * 여기서 나온다.
     */
    private void logTop(Map<Integer, Double> scores) {
        String top = scores.entrySet().stream()
                .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed()
                        .thenComparing(Map.Entry::getKey))
                .limit(10)
                .map(entry -> "%d=%.4f".formatted(entry.getKey(), entry.getValue()))
                .reduce((a, b) -> a + " " + b)
                .orElse("");
        log.info("아티스트 랭킹 상위 10 — {}", top);
    }
}
