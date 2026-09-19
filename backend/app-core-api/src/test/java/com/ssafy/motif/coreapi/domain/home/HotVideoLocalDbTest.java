package com.ssafy.motif.coreapi.domain.home;

import com.ssafy.motif.common.response.CursorResponse;
import com.ssafy.motif.coreapi.domain.home.dto.HotVideoItem;
import com.ssafy.motif.coreapi.domain.home.service.HotVideoQueryService;
import com.ssafy.motif.coreapi.domain.video.ranking.VideoRankingSnapshot;
import com.ssafy.motif.coreapi.domain.video.ranking.VideoRankingSnapshotJob;
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
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 홈 HOT — DB·Redis 통합 (S15P21D104-113 · 명세 §6.1).
 *
 * <p>단위 테스트가 닿지 못하는 곳만 본다 — <b>JPQL 두 개가 실제로 도는지</b>
 * ({@code findRankingInputs}·{@code findHotCards}. 엔티티 조인과 생성자 투영이라
 * 문법이 맞아도 런타임에 깨질 수 있다)와 <b>진짜 Redis 에서 버전 스냅샷이 왕복하는지</b>.
 * 점수 공식·커서 계약은 각각 {@code VideoRankingScoreTest}·{@code HotVideoQueryServiceTest}
 * 가 이미 고정한다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 *
 * <p><b>Redis 키는 롤백되지 않는다.</b> {@code @Transactional} 은 DB 만 되돌리므로
 * 스냅샷 키는 {@code @AfterEach} 에서 직접 지운다 — 남으면 다음 실행이 지난 순위를 보고
 * 엉뚱하게 실패한다({@code ArtistRankingLocalDbTest} 와 같은 이유).
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class HotVideoLocalDbTest {

    @Autowired
    private VideoRankingSnapshotJob job;
    @Autowired
    private VideoRankingSnapshot snapshot;
    @Autowired
    private HotVideoQueryService hotVideoQueryService;
    @Autowired
    private StringRedisTemplate redis;
    @PersistenceContext
    private EntityManager entityManager;

    private Integer me;
    private Integer artistSeq;
    private Integer challengeSeq;

    @BeforeEach
    void setUp() {
        me = newUser();
        artistSeq = newArtist();
        challengeSeq = newChallenge(artistSeq, newSong(), false);
        clearSnapshots();
    }

    @AfterEach
    void tearDown() {
        clearSnapshots();
    }

    /** 포인터와 이번 실행이 만든 버전 키를 모두 지운다. */
    private void clearSnapshots() {
        Set<String> versions = redis.keys("rank:video:*");
        if (versions != null && !versions.isEmpty()) {
            redis.delete(versions);
        }
    }

    @Test
    @DisplayName("재료 JPQL 이 돈다 — PUBLIC 비삭제 영상이 순위에 오른다")
    void rankingInputsQueryRuns() {
        Long videoSeq = newVideo(1_000L, "PUBLIC", false, Instant.now());

        job.refresh();

        assertThat(snapshot.currentVersion()).isNotNull();
        assertThat(snapshot.page(snapshot.currentVersion(), 0, 100))
                .extracting(VideoRankingSnapshot.Ranked::videoSeq)
                .contains(videoSeq);
    }

    @Test
    @DisplayName("PRIVATE 영상은 후보가 아니다")
    void privateVideoIsNotACandidate() {
        Long videoSeq = newVideo(50_000L, "PRIVATE", false, Instant.now());

        job.refresh();

        assertThat(rankedSeqs()).doesNotContain(videoSeq);
    }

    @Test
    @DisplayName("삭제된 영상은 후보가 아니다")
    void deletedVideoIsNotACandidate() {
        Long videoSeq = newVideo(50_000L, "PUBLIC", true, Instant.now());

        job.refresh();

        assertThat(rankedSeqs()).doesNotContain(videoSeq);
    }

    @Test
    @DisplayName("삭제된 챌린지에 속한 영상은 후보가 아니다 — §1.8 공통 비노출")
    void videoOfDeletedChallengeIsNotACandidate() {
        challengeSeq = newChallenge(artistSeq, newSong(), true);
        Long videoSeq = newVideo(50_000L, "PUBLIC", false, Instant.now());

        job.refresh();

        assertThat(rankedSeqs()).doesNotContain(videoSeq);
    }

    @Test
    @DisplayName("보호 프리픽스라고 빼지 않는다 — 참여 영상은 전부 보호 등급이다 (ADR-0013)")
    void protectedPrefixIsStillACandidate() {
        Long videoSeq = newVideoWithKeyPrefix(50_000L, "private/challenge/");

        job.refresh();

        assertThat(rankedSeqs()).contains(videoSeq);
    }

    @Test
    @DisplayName("조회수가 많아도 오래된 영상이 최근 영상에 밀린다 — 실제 쿼리 경로로")
    void decayLetsRecentVideoOvertake() {
        Long 옛날 = newVideo(10_000L, "PUBLIC", false, Instant.now().minus(Duration.ofDays(30)));
        Long 최근 = newVideo(1_000L, "PUBLIC", false, Instant.now().minus(Duration.ofHours(6)));

        job.refresh();

        List<Long> ranked = rankedSeqs();
        assertThat(ranked.indexOf(최근)).isLessThan(ranked.indexOf(옛날));
    }

    @Test
    @DisplayName("카드 JPQL 이 돈다 — 곡·작성자가 채워져 내려간다")
    void hotCardQueryRuns() {
        // 로컬 DB 에 남아 있는 다른 영상보다 확실히 앞에 오도록 크게 준다 —
        // 첫 페이지에 못 들면 이 테스트가 쿼리와 무관한 이유로 깨진다
        Long videoSeq = newVideo(10_000_000L, "PUBLIC", false, Instant.now());
        job.refresh();

        CursorResponse<HotVideoItem> response = hotVideoQueryService.hotVideos(null, 100);

        assertThat(response.list()).isNotEmpty();
        assertThat(response.list())
                .filteredOn(item -> item.videoSeq().equals(videoSeq))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.rank()).isPositive();
                    assertThat(item.song().title()).isNotBlank();
                    assertThat(item.song().singerName()).isNotBlank();
                    assertThat(item.author().userSeq()).isEqualTo(me);
                    assertThat(item.author().nickname()).isNotBlank();
                    assertThat(item.durationMs()).isEqualTo(15000);
                });
    }

    @Test
    @DisplayName("커서로 이어 받으면 겹치지도 빠지지도 않는다 — 진짜 Redis 왕복")
    void cursorWalksTheSnapshotWithoutGapsOrDuplicates() {
        for (int i = 0; i < 5; i++) {
            newVideo(1_000L * (i + 1), "PUBLIC", false, Instant.now());
        }
        job.refresh();

        CursorResponse<HotVideoItem> first = hotVideoQueryService.hotVideos(null, 2);
        assertThat(first.list()).hasSize(2);
        assertThat(first.hasNext()).isTrue();

        CursorResponse<HotVideoItem> second =
                hotVideoQueryService.hotVideos(first.nextCursor(), 2);

        assertThat(second.list()).hasSize(2);
        assertThat(second.list()).extracting(HotVideoItem::videoSeq)
                .doesNotContainAnyElementsOf(
                        first.list().stream().map(HotVideoItem::videoSeq).toList());
        assertThat(second.list()).extracting(HotVideoItem::rank).containsExactly(3, 4);
    }

    @Test
    @DisplayName("스냅샷이 없으면 빈 목록 — 배치 전에도 API 가 살아 있다")
    void emptyBeforeFirstBatch() {
        newVideo(1_000L, "PUBLIC", false, Instant.now());

        CursorResponse<HotVideoItem> response = hotVideoQueryService.hotVideos(null, 10);

        assertThat(response.list()).isEmpty();
        assertThat(response.hasNext()).isFalse();
    }

    @Test
    @DisplayName("없는 버전의 커서는 빈 목록이다 — 오류가 아니다")
    void unknownVersionCursorIsEmpty() {
        newVideo(1_000L, "PUBLIC", false, Instant.now());
        job.refresh();

        CursorResponse<HotVideoItem> response = hotVideoQueryService.hotVideos(
                com.ssafy.motif.coreapi.global.response.Cursors.encodeSnapshot(1L, 0), 10);

        assertThat(response.list()).isEmpty();
        assertThat(response.hasNext()).isFalse();
    }

    private List<Long> rankedSeqs() {
        Long version = snapshot.currentVersion();
        if (version == null) {
            return List.of();
        }
        return snapshot.page(version, 0, 1000).stream()
                .map(VideoRankingSnapshot.Ranked::videoSeq)
                .toList();
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

    private Integer newChallenge(Integer artistSeq, Integer songSeq, boolean deleted) {
        String uuid = UUID.randomUUID().toString();
        execute("""
                insert into motif_core.challenges
                    (artist_seq, song_seq, description, video_key, thumbnail_key, duration_ms,
                     is_deleted)
                values (?1, ?2, '소개글', ?3, ?4, 15000, ?5)
                """, artistSeq, songSeq,
                "challenges/" + uuid + ".mp4", "challenges/" + uuid + "/thumb.jpg", deleted);
        return scalar("select challenge_seq from motif_core.challenges where video_key = ?1",
                "challenges/" + uuid + ".mp4");
    }

    private Long newVideo(long viewCount, String visibility, boolean deleted, Instant regDttm) {
        return newVideo(viewCount, visibility, deleted, regDttm, "private/challenge/");
    }

    /** 프리픽스가 후보 선정에 영향을 주지 않는다는 것을 고정하는 용도. */
    private Long newVideoWithKeyPrefix(long viewCount, String prefix) {
        return newVideo(viewCount, "PUBLIC", false, Instant.now(), prefix);
    }

    /**
     * {@code composite_jobs} → {@code participation_videos}.
     *
     * <p>잡을 먼저 만드는 것은 {@code job_seq} 가 NOT NULL FK 이기 때문이다. 게시된
     * 영상이므로 잡은 {@code DONE} 이어야 한다({@code ck_composite_jobs_status_shape}
     * 가 결과 키를 함께 요구한다).
     */
    private Long newVideo(long viewCount, String visibility, boolean deleted,
                          Instant regDttm, String prefix) {
        String uuid = UUID.randomUUID().toString();
        String resultKey = prefix + uuid + "/master.m3u8";
        execute("""
                insert into motif_core.composite_jobs
                    (job_uuid, job_type, user_seq, challenge_seq, raw_video_key, status,
                     result_video_key, result_thumbnail_key)
                values (cast(?1 as uuid), 'CHALLENGE', ?2, ?3, ?4, 'DONE', ?5, ?6)
                """, uuid, me, challengeSeq, "raw/" + uuid + ".mp4",
                resultKey, prefix + uuid + "/thumb.jpg");
        Long jobSeq = ((Number) entityManager
                .createNativeQuery("select job_seq from motif_core.composite_jobs where job_uuid = cast(?1 as uuid)")
                .setParameter(1, uuid)
                .getSingleResult()).longValue();

        execute("""
                insert into motif_core.participation_videos
                    (challenge_seq, artist_seq, user_seq, job_seq, description, video_key,
                     thumbnail_key, duration_ms, visibility, view_count, reg_dttm, is_deleted)
                values (?1, ?2, ?3, ?4, '설명', ?5, ?6, 15000, ?7, ?8, ?9, ?10)
                """, challengeSeq, artistSeq, me, jobSeq, resultKey,
                prefix + uuid + "/thumb.jpg", visibility, viewCount, regDttm, deleted);
        entityManager.flush();
        entityManager.clear();

        return ((Number) entityManager
                .createNativeQuery("select video_seq from motif_core.participation_videos where job_seq = ?1")
                .setParameter(1, jobSeq)
                .getSingleResult()).longValue();
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
}
