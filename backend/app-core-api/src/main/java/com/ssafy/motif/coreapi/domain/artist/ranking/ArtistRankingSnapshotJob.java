package com.ssafy.motif.coreapi.domain.artist.ranking;

import com.ssafy.motif.domain.core.artist.ArtistRepository;
import com.ssafy.motif.domain.core.challenge.ChallengeRepository;
import com.ssafy.motif.domain.core.challenge.ChallengeScoreRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 아티스트 랭킹 스냅샷 배치 (API 명세 §5.1) — §5.1 API 는 이 배치가 만든 것을 읽기만 한다.
 *
 * <p><b>왜 두 주기인가.</b> {@code :current} 는 §1.6 이 클라 캐시 TTL 5분을 "랭킹 배치
 * 주기와 동일"로 묶어 놔서 5분이다. {@code :prev} 를 같은 주기로 밀면 {@code rankDelta}
 * 의 대비 구간이 한 틱뿐이라 변동이 거의 잡히지 않으므로 더 길게 간다.
 *
 * <p><b>ShedLock 을 쓰지 않는다.</b> core-api 는 1 레플리카고(ADR-0009) 이 배치는 상태를
 * 누적하지 않는 <b>전량 재계산</b>이라 중복 실행돼도 결과가 같다. 다중화되면 어드민
 * §3.16.4(배치 수동 실행 + 락 409) 티켓에서 함께 넣는다.
 *
 * <p>주기 등록은 {@link RankingConfig} 가 한다 — 설정의 {@code Duration} 을 그대로 쓰기
 * 위해서다({@code @Scheduled} 는 문자열만 받는다).
 *
 * <p><b>예외를 삼킨다.</b> 스케줄된 작업이 예외를 던지면 로그만 남고 다음 주기에 다시
 * 돈다 — 스케줄 자체는 죽지 않는다. 그래도 여기서 잡아 로그를 남기는 것은 DB·Redis 중
 * 어느 쪽에서 실패했는지가 스택만으로는 잘 안 보이기 때문이다. 실패해도 직전 스냅샷이
 * 그대로 살아 있다(키에 TTL 이 없다 — {@link ArtistRankingSnapshot}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArtistRankingSnapshotJob {

    private final ArtistRepository artistRepository;
    private final ChallengeRepository challengeRepository;
    private final RankingScore rankingScore;
    private final ArtistRankingSnapshot snapshot;

    /**
     * 현재 순위 갱신 — 기본 5분.
     *
     * <p>{@code :prev} 가 없으면(최초 배포) 같은 내용으로 함께 심는다. 그래야 첫 화면에서
     * {@code rankDelta} 가 전원 <b>0(변동 없음)</b> 이 되고, {@code null} 이 <b>진짜 신규
     * 아티스트</b> 전용으로 남는다 — 배포 직후 전원이 "신규 진입"으로 보이는 것보다 낫다.
     */
    public void refreshCurrent() {
        try {
            Map<Integer, Double> scores = computeScores();
            snapshot.replaceCurrent(scores);

            if (!snapshot.hasPrevious()) {
                snapshot.replacePrevious(scores);
                log.info("아티스트 랭킹 최초 스냅샷 — prev 를 current 와 같게 심었다 ({}명)",
                        scores.size());
            }
        } catch (Exception e) {
            log.error("아티스트 랭킹 current 스냅샷 갱신 실패 — 직전 스냅샷을 유지한다", e);
        }
    }

    /**
     * {@code rankDelta} 의 기준이 되는 직전 순위 갱신 — 기본 10분.
     *
     * <p>{@code :current} 를 복사하지 않고 <b>같은 집계를 다시 돌린다.</b> Redis
     * {@code COPY} 는 6.2+ 인데 운영 Redis 버전이 확인되지 않았고(로컬만
     * {@code redis:7-alpine}), 수백 건 재적재가 버전 호환을 따지는 것보다 싸다.
     * 두 스냅샷의 시차는 최대 한 틱이라 대비 구간에 영향이 없다.
     */
    public void refreshPrevious() {
        try {
            snapshot.replacePrevious(computeScores());
        } catch (Exception e) {
            log.error("아티스트 랭킹 prev 스냅샷 갱신 실패 — 직전 기준을 유지한다", e);
        }
    }

    /**
     * 비삭제 아티스트 전량 + 비삭제 챌린지 전량을 읽어 점수를 낸다 — 쿼리 2회.
     *
     * <p><b>트랜잭션으로 묶지 않는다.</b> 두 조회 사이에 아티스트가 삭제되면 주인 없는
     * 챌린지가 남는데, 그건 {@link RankingScore#compute} 가 이미 버린다. 묶어 봐야
     * 다음 주기(5분 뒤)에 어차피 다시 계산되는 값이고, {@code @Scheduled} 메서드에서
     * 이 메서드를 직접 부르면 프록시를 타지 않아 {@code @Transactional} 이 조용히
     * 무시된다 — 붙어 있는데 안 도는 어노테이션이 없는 것보다 나쁘다.
     */
    public Map<Integer, Double> computeScores() {
        List<Integer> artistSeqs = artistRepository.findRankableSeqs();
        List<ChallengeScoreRow> challenges = challengeRepository.findRankingInputs();
        return rankingScore.compute(artistSeqs, challenges, Instant.now());
    }
}
