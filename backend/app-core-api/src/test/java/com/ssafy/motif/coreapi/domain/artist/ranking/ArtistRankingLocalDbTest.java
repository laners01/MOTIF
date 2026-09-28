package com.ssafy.motif.coreapi.domain.artist.ranking;

import com.ssafy.motif.common.response.PageResponse;
import com.ssafy.motif.coreapi.domain.artist.dto.ArtistRankingResponse;
import com.ssafy.motif.coreapi.domain.artist.dto.RankingFilter;
import com.ssafy.motif.coreapi.domain.artist.service.ArtistRankingQueryService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import com.ssafy.motif.coreapi.domain.view.ViewBuffer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 아티스트 랭킹 — DB·Redis 통합 (S15P21D104-51 · 명세 §5.1).
 *
 * <p>단위 테스트가 닿지 못하는 곳만 본다 — <b>JPQL 이 실제로 도는지</b>
 * ({@code findRankingInputs}·{@code findRankableSeqs}·{@code findCards})와
 * <b>진짜 Redis 에서 ZSET 교체가 되는지</b>. 점수 공식과 응답 조립은 각각
 * {@code RankingScoreTest}·{@code ArtistRankingQueryServiceTest} 가 이미 고정한다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 *
 * <p><b>Redis 키는 롤백되지 않는다.</b> {@code @Transactional} 은 DB 만 되돌리므로
 * 스냅샷 키는 {@code @AfterEach} 에서 직접 지운다 — 개발자 로컬에 남으면 다음 실행이
 * 남은 순위를 보고 엉뚱하게 실패한다.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class ArtistRankingLocalDbTest {

    @Autowired
    private ArtistRankingSnapshotJob job;
    @Autowired
    private ArtistRankingSnapshot snapshot;
    @Autowired
    private ArtistRankingQueryService rankingQueryService;
    @Autowired
    @Qualifier("challengeViewBuffer")
    private ViewBuffer challengeViewBuffer;
    @Autowired
    private StringRedisTemplate redis;
    @PersistenceContext
    private EntityManager entityManager;

    private Integer me;

    @BeforeEach
    void setUp() {
        me = newUser();
        clearSnapshots();
    }

    @AfterEach
    void tearDown() {
        clearSnapshots();
    }

    /** 버전 키가 갱신마다 새 이름이라 패턴으로 지운다. 챌린지 버킷도 함께 치운다. */
    private void clearSnapshots() {
        Set<String> keys = redis.keys("rank:artist:*");
        keys.addAll(redis.keys("view:challenge:bucket*"));
        if (!keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    @Test
    @DisplayName("집계 쿼리 2개가 실제로 돈다 — 비삭제 아티스트 전원이 점수를 받는다")
    void 집계_쿼리() {
        Integer artistSeq = newArtist();
        Integer songSeq = newSong();
        newChallenge(artistSeq, songSeq, 1_000, 0, Instant.now(), false);

        Map<Integer, Double> scores = job.computeScores();

        assertThat(scores).containsKey(artistSeq);
        assertThat(scores.get(artistSeq)).isGreaterThan(0.0);
    }

    /**
     * <b>바닥 점수가 0 이 아니다.</b> 모멘텀의 "변동 없음" 이 0.5 라 반응이 전무한
     * 아티스트도 {@code Wd × 0.5} 를 받는다 — 그래서 값을 박지 않고 "반응이 없는
     * 아티스트끼리 같다" 로 고정한다.
     */
    @Test
    @DisplayName("삭제된 챌린지는 점수에 들어가지 않는다 — 신인과 같은 바닥 점수다")
    void 삭제_챌린지_제외() {
        Integer artistSeq = newArtist();
        Integer rookie = newArtist();
        Integer songSeq = newSong();
        newChallenge(artistSeq, songSeq, 50_000, 0, Instant.now(), true);

        Map<Integer, Double> scores = job.computeScores();

        assertThat(scores.get(artistSeq)).isEqualTo(scores.get(rookie));
    }

    @Test
    @DisplayName("챌린지가 없는 아티스트도 스냅샷에 들어간다 — 빼면 온보딩에서 신인이 사라진다")
    void 신인_포함() {
        Integer rookie = newArtist();
        Integer 반응_있는 = newArtist();
        newChallenge(반응_있는, newSong(), 1_000, 0, Instant.now(), false);

        Map<Integer, Double> scores = job.computeScores();

        assertThat(scores).containsKey(rookie);
        assertThat(scores.get(반응_있는)).isGreaterThan(scores.get(rookie));
    }

    @Test
    @DisplayName("삭제된 아티스트는 스냅샷 대상이 아니다")
    void 삭제_아티스트_제외() {
        Integer artistSeq = newArtist();
        execute("update motif_core.artists set is_deleted = true where artist_seq = ?1", artistSeq);
        entityManager.flush();
        entityManager.clear();

        assertThat(job.computeScores()).doesNotContainKey(artistSeq);
    }

    @Test
    @DisplayName("진짜 Redis 에 적재되고 순서대로 다시 읽힌다")
    void 스냅샷_왕복() {
        Integer 낮은_점수 = newArtist();
        Integer 높은_점수 = newArtist();

        snapshot.replace(Map.of(낮은_점수, 10.0, 높은_점수, 500.0));

        assertThat(snapshot.read().ranking()).startsWith(높은_점수, 낮은_점수);
    }

    @Test
    @DisplayName("배치가 한 번 돌면 스냅샷이 선다 — 전원 '변동 없음' 이고 신규 진입이 아니다")
    void 배치_최초_실행() {
        Integer artistSeq = newArtist();

        job.refresh();

        ArtistRankingSnapshot.Snapshot current = snapshot.read();
        assertThat(current.ranking()).contains(artistSeq);
        // null 은 진짜 신규 아티스트 전용이다 — 배포 직후 전원이 "신규" 로 보이면 안 된다
        assertThat(current.deltas()).containsEntry(artistSeq, 0);
    }

    @Test
    @DisplayName("조회 API 가 스냅샷 순서대로 메타를 채워 내려준다")
    void 조회_통합() {
        Integer 일위 = newArtist();
        Integer 이위 = newArtist();
        // 대비가 3틱 전이라 기준을 세 번 깔고 네 번째에 순서를 뒤집는다 —
        // 일위는 2→1(+1), 이위는 1→2(-1)
        Map<Integer, Double> 직전 = Map.of(이위, 500.0, 일위, 10.0);
        snapshot.replace(직전);
        snapshot.replace(직전);
        snapshot.replace(직전);
        snapshot.replace(Map.of(일위, 500.0, 이위, 10.0));

        PageResponse<ArtistRankingResponse> page =
                rankingQueryService.ranking(me, RankingFilter.ALL, 0, 100);

        assertThat(page.list()).extracting(ArtistRankingResponse::artistSeq)
                .containsSubsequence(일위, 이위);
        assertThat(page.list()).allSatisfy(item -> assertThat(item.name()).isNotBlank());
        assertThat(page.list()).filteredOn(item -> item.artistSeq().equals(일위))
                .singleElement().extracting(ArtistRankingResponse::rankDelta).isEqualTo(1);
        assertThat(page.list()).filteredOn(item -> item.artistSeq().equals(이위))
                .singleElement().extracting(ArtistRankingResponse::rankDelta).isEqualTo(-1);
    }

    @Test
    @DisplayName("SUBSCRIBED 필터가 진짜 구독 행을 본다")
    void 구독_필터() {
        Integer 구독한 = newArtist();
        Integer 구독_안한 = newArtist();
        execute("insert into motif_core.subscriptions (user_seq, artist_seq) values (?1, ?2)",
                me, 구독한);
        snapshot.replace(Map.of(구독한, 500.0, 구독_안한, 10.0));

        assertThat(rankingQueryService.ranking(me, RankingFilter.SUBSCRIBED, 0, 100).list())
                .extracting(ArtistRankingResponse::artistSeq)
                .containsExactly(구독한);
        assertThat(rankingQueryService.ranking(me, RankingFilter.UNSUBSCRIBED, 0, 100).list())
                .extracting(ArtistRankingResponse::artistSeq)
                .containsExactly(구독_안한);
    }

    @Test
    @DisplayName("스냅샷이 없으면 빈 목록 — 배치 전에도 API 가 살아 있다")
    void 스냅샷_없음() {
        PageResponse<ArtistRankingResponse> page =
                rankingQueryService.ranking(me, RankingFilter.ALL, 0, 20);

        assertThat(page.list()).isEmpty();
        assertThat(page.totalElements()).isZero();
    }

    // --- 픽스처 -------------------------------------------------------------

    private Integer newUser() {
        String nickname = "t-" + UUID.randomUUID().toString().substring(0, 20);
        execute("insert into motif_core.users (nickname, role) values (?1, 'USER')", nickname);
        return scalar("select user_seq from motif_core.users where nickname = ?1", nickname);
    }

    /** {@code users(ARTIST)} → {@code companies} → {@code artists} FK 체인. */
    private Integer newArtist() {
        Integer userSeq = newUser();
        String companyName = "t-" + UUID.randomUUID().toString().substring(0, 20);
        execute("insert into motif_core.companies (company_name, reg_usr_seq) values (?1, ?2)",
                companyName, userSeq);
        Integer companySeq = scalar(
                "select company_seq from motif_core.companies where company_name = ?1", companyName);

        execute("insert into motif_core.artists (user_seq, company_seq, reg_usr_seq) values (?1, ?2, ?1)",
                userSeq, companySeq);
        return scalar("select artist_seq from motif_core.artists where user_seq = ?1", userSeq);
    }

    private Integer newSong() {
        String title = "t-" + UUID.randomUUID().toString().substring(0, 20);
        execute("insert into motif_core.songs (title, singer_name, reg_usr_seq) values (?1, '가수', ?2)",
                title, me);
        return scalar("select song_seq from motif_core.songs where title = ?1", title);
    }

    /** @return 만든 {@code challengeSeq} — 최근 조회수 버킷의 field 가 이 값이다. */
    private Integer newChallenge(Integer artistSeq, Integer songSeq, long viewCount,
                                 int participantCount, Instant regDttm, boolean deleted) {
        String uuid = UUID.randomUUID().toString();
        String videoKey = "challenges/" + uuid + ".mp4";
        execute("""
                insert into motif_core.challenges
                    (artist_seq, song_seq, description, video_key, thumbnail_key, duration_ms,
                     view_count, participant_count, reg_dttm, is_deleted)
                values (?1, ?2, '소개글', ?3, ?4, 15000, ?5, ?6, ?7, ?8)
                """, artistSeq, songSeq,
                videoKey, "challenges/" + uuid + "/thumb.jpg",
                viewCount, participantCount, regDttm, deleted);
        entityManager.flush();
        entityManager.clear();
        return scalar("select challenge_seq from motif_core.challenges where video_key = ?1",
                videoKey);
    }

    private void execute(String sql, Object... params) {
        var query = entityManager.createNativeQuery(sql);
        for (int i = 0; i < params.length; i++) {
            query.setParameter(i + 1, params[i]);
        }
        query.executeUpdate();
    }

    private Integer scalar(String sql, Object param) {
        return ((Number) entityManager.createNativeQuery(sql)
                .setParameter(1, param)
                .getSingleResult()).intValue();
    }

    /** 감쇠 검증용 — 나이 차가 순위를 뒤집는지 본다. */
    @Test
    @DisplayName("참여 영상 수가 누적 반응량을 끌어올린다 — 실제 쿼리 경로로")
    void 참여_반영() {
        Integer 참여_많은 = newArtist();
        Integer 조회만 = newArtist();
        Integer songSeq = newSong();
        newChallenge(참여_많은, songSeq, 1_000, 50, Instant.now(), false);
        newChallenge(조회만, songSeq, 1_000, 0, Instant.now(), false);

        Map<Integer, Double> scores = job.computeScores();

        assertThat(scores.get(참여_많은)).isGreaterThan(scores.get(조회만));
    }

    /**
     * 게시 시각을 반대로 준다 — 시간 감쇠를 걷어냈으므로(ADR-0016) 순서에 영향이 없어야
     * 한다. 구 공식이었다면 30일 된 챌린지가 계수 0.2264 에 눌려 뒤집혔다.
     */
    @Test
    @DisplayName("오래된 챌린지라고 밀리지 않는다 — 감쇠가 되살아나면 깨진다")
    void 감쇠_없음() {
        Integer 옛날 = newArtist();
        Integer 최근 = newArtist();
        Integer songSeq = newSong();
        newChallenge(옛날, songSeq, 10_000, 0, Instant.now().minus(Duration.ofDays(30)), false);
        newChallenge(최근, songSeq, 3_000, 0, Instant.now().minus(Duration.ofDays(2)), false);

        Map<Integer, Double> scores = job.computeScores();

        assertThat(scores.get(옛날)).isGreaterThan(scores.get(최근));
    }

    @Test
    @DisplayName("최근 조회수가 챌린지 버킷에서 아티스트로 합산된다 — 실제 Redis 왕복")
    void 버킷_합산() {
        Integer 지금_도는 = newArtist();
        Integer 조용한 = newArtist();
        Integer songSeq = newSong();
        Integer 도는_챌린지 = newChallenge(지금_도는, songSeq, 3_000, 0, Instant.now(), false);
        newChallenge(조용한, songSeq, 3_000, 0, Instant.now(), false);
        challengeViewBuffer.saveBucket("t1", Map.of(도는_챌린지.longValue(), 900L));

        Map<Integer, Double> scores = job.computeScores();

        assertThat(scores.get(지금_도는)).isGreaterThan(scores.get(조용한));
    }
}
