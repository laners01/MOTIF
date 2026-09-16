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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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

    private void clearSnapshots() {
        redis.delete(List.of("rank:artist:current", "rank:artist:prev",
                "rank:artist:current:building", "rank:artist:prev:building"));
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

    @Test
    @DisplayName("삭제된 챌린지는 점수에 들어가지 않는다")
    void 삭제_챌린지_제외() {
        Integer artistSeq = newArtist();
        Integer songSeq = newSong();
        newChallenge(artistSeq, songSeq, 50_000, 0, Instant.now(), true);

        assertThat(job.computeScores()).containsEntry(artistSeq, 0.0);
    }

    @Test
    @DisplayName("챌린지가 없는 아티스트도 0점으로 스냅샷에 들어간다")
    void 신인_포함() {
        Integer rookie = newArtist();

        assertThat(job.computeScores()).containsEntry(rookie, 0.0);
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
    @DisplayName("진짜 Redis 에 적재·RENAME 되고 다시 읽힌다")
    void 스냅샷_왕복() {
        Integer 낮은_점수 = newArtist();
        Integer 높은_점수 = newArtist();

        snapshot.replaceCurrent(Map.of(낮은_점수, 10.0, 높은_점수, 500.0));

        assertThat(snapshot.current()).startsWith(높은_점수, 낮은_점수);
        assertThat(redis.hasKey("rank:artist:current:building")).isFalse();
    }

    @Test
    @DisplayName("최초 실행이면 prev 도 함께 심는다 — 전원이 '신규 진입' 으로 보이지 않게")
    void 최초_실행() {
        newArtist();

        assertThat(snapshot.hasPrevious()).isFalse();
        job.refreshCurrent();

        assertThat(snapshot.hasPrevious()).isTrue();
        assertThat(snapshot.previousRanks()).isNotEmpty();
    }

    @Test
    @DisplayName("이미 prev 가 있으면 current 갱신이 덮지 않는다 — rankDelta 기준이 유지된다")
    void 기존_prev_유지() {
        Integer artistSeq = newArtist();
        snapshot.replacePrevious(Map.of(artistSeq, 999.0));

        job.refreshCurrent();

        assertThat(snapshot.previousRanks()).containsKey(artistSeq);
    }

    @Test
    @DisplayName("조회 API 가 스냅샷 순서대로 메타를 채워 내려준다")
    void 조회_통합() {
        Integer 일위 = newArtist();
        Integer 이위 = newArtist();
        snapshot.replaceCurrent(Map.of(일위, 500.0, 이위, 10.0));
        // 직전에는 순서가 반대였다 — 일위는 2→1(+1), 이위는 1→2(-1)
        snapshot.replacePrevious(Map.of(이위, 500.0, 일위, 10.0));

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
        snapshot.replaceCurrent(Map.of(구독한, 500.0, 구독_안한, 10.0));

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

    private void newChallenge(Integer artistSeq, Integer songSeq, long viewCount,
                              int participantCount, Instant regDttm, boolean deleted) {
        String uuid = UUID.randomUUID().toString();
        execute("""
                insert into motif_core.challenges
                    (artist_seq, song_seq, description, video_key, thumbnail_key, duration_ms,
                     view_count, participant_count, reg_dttm, is_deleted)
                values (?1, ?2, '소개글', ?3, ?4, 15000, ?5, ?6, ?7, ?8)
                """, artistSeq, songSeq,
                "challenges/" + uuid + ".mp4", "challenges/" + uuid + "/thumb.jpg",
                viewCount, participantCount, regDttm, deleted);
        entityManager.flush();
        entityManager.clear();
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
    @DisplayName("조회수가 많아도 오래된 챌린지가 최근 챌린지에 밀린다 — 실제 쿼리 경로로")
    void 감쇠_역전() {
        Integer 옛날 = newArtist();
        Integer 최근 = newArtist();
        Integer songSeq = newSong();
        newChallenge(옛날, songSeq, 10_000, 0, Instant.now().minus(Duration.ofDays(30)), false);
        newChallenge(최근, songSeq, 3_000, 0, Instant.now().minus(Duration.ofDays(2)), false);

        Map<Integer, Double> scores = job.computeScores();

        assertThat(scores.get(최근)).isGreaterThan(scores.get(옛날));
    }
}
