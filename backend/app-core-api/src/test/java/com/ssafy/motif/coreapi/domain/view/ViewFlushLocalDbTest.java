package com.ssafy.motif.coreapi.domain.view;

import com.ssafy.motif.coreapi.domain.video.VideoTestData;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 조회수 집계 — Redis·DB 통합 (S15P21D104-130 · ADR-0015).
 *
 * <p>단위 테스트가 닿지 못하는 곳만 본다 — <b>RENAMENX 가 정말 얼리는지</b>,
 * <b>배열 {@code unnest} UPDATE 가 실제로 도는지</b>, <b>재처리가 두 번 더하지
 * 않는지</b>. 단계 순서는 {@code ViewFlushJobTest} 가 이미 고정한다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 *
 * <p><b>Redis 키는 롤백되지 않는다.</b> {@code @Transactional} 은 DB 만 되돌리므로 조회수
 * 키는 직접 지운다({@code ArtistRankingLocalDbTest} 와 같은 규칙).
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class ViewFlushLocalDbTest {

    private static final Instant NOW = Instant.parse("2026-09-21T09:40:12Z");
    private static final String TS = "20260921094012";

    @Autowired
    @Qualifier("videoViewBuffer")
    private ViewBuffer buffer;
    @Autowired
    private ViewFlushJob job;
    @Autowired
    private StringRedisTemplate redis;
    @PersistenceContext
    private EntityManager entityManager;

    private VideoTestData data;
    private Long videoSeq;

    @BeforeEach
    void setUp() {
        clearKeys();
        data = new VideoTestData(entityManager);
        Integer artistSeq = data.newArtist();
        Integer owner = data.newUser();
        Integer songSeq = data.newSong(owner);
        Integer challengeSeq = data.newChallenge(artistSeq, songSeq, false);
        videoSeq = data.video(challengeSeq, artistSeq).owner(owner).views(0).insert();
    }

    @AfterEach
    void tearDown() {
        clearKeys();
    }

    private void clearKeys() {
        redis.delete(List.of(
                buffer.pendingKey(),
                buffer.processingIndexKey(),
                buffer.bucketIndexKey(),
                buffer.processingKey(TS),
                buffer.bucketKey(TS),
                buffer.flushMarkerKey(TS)));
    }

    @Test
    @DisplayName("누적분이 DB 에 반영되고 버킷이 생기며 processing 이 치워진다")
    void flushesPendingIntoDbAndBucket() {
        buffer.add(Map.of(videoSeq, 5));

        job.flush(NOW);

        assertThat(viewCount()).isEqualTo(5L);
        assertThat(redis.opsForHash().get(buffer.bucketKey(TS), String.valueOf(videoSeq)))
                .isEqualTo("5");
        assertThat(redis.opsForList().range(buffer.bucketIndexKey(), 0, -1)).containsExactly(TS);

        // 얼린 스냅샷과 미완료 인덱스가 함께 비어야 끝난 것이다
        assertThat(redis.hasKey(buffer.processingKey(TS))).isFalse();
        assertThat(redis.opsForList().size(buffer.processingIndexKey())).isZero();
        // rotate 가 가져갔으므로 pending 은 사라진다 — 다음 조회는 새 키에 쌓인다
        assertThat(redis.hasKey(buffer.pendingKey())).isFalse();
    }

    @Test
    @DisplayName("버킷에는 TTL 이 걸린다 — 걸리지 않으면 지난 구간이 무한히 쌓인다")
    void bucketExpires() {
        buffer.add(Map.of(videoSeq, 1));

        job.flush(NOW);

        assertThat(redis.getExpire(buffer.bucketKey(TS))).isPositive();
    }

    @Test
    @DisplayName("DB 반영 이후 후처리 전에 죽어도 재처리가 두 번 더하지 않는다")
    void doesNotDoubleApplyOnReprocess() {
        buffer.add(Map.of(videoSeq, 5));
        job.flush(NOW);
        assertThat(viewCount()).isEqualTo(5L);

        // "DB 는 넣었는데 processing 을 못 지우고 죽었다" 를 그대로 재현한다 —
        // 마커는 살아 있고 얼린 스냅샷도 남아 있는 상태
        redis.opsForHash().put(buffer.processingKey(TS), String.valueOf(videoSeq), "5");
        redis.opsForList().leftPush(buffer.processingIndexKey(), TS);

        job.flush(NOW.plusSeconds(1200));

        assertThat(viewCount()).isEqualTo(5L);
        assertThat(redis.hasKey(buffer.processingKey(TS))).isFalse();
    }

    @Test
    @DisplayName("얼린 뒤 들어온 조회수는 새 pending 에 쌓여 다음 주기가 가져간다")
    void keepsViewsArrivingAfterRotate() {
        buffer.add(Map.of(videoSeq, 3));
        job.flush(NOW);

        buffer.add(Map.of(videoSeq, 2));

        assertThat(viewCount()).isEqualTo(3L);
        assertThat(redis.opsForHash().get(buffer.pendingKey(), String.valueOf(videoSeq)))
                .isEqualTo("2");
    }

    @Test
    @DisplayName("존재하지 않는 videoSeq 는 조용히 버려진다 — 예외도 롤백도 없다")
    void ignoresUnknownVideoSeq() {
        buffer.add(Map.of(videoSeq, 2, 999_999_999L, 7));

        assertThatCode(() -> job.flush(NOW)).doesNotThrowAnyException();

        assertThat(viewCount()).isEqualTo(2L);
        // 버킷에는 남는다 — 랭킹 모집단이 DB 에서 나오므로 순위에 오르지는 않는다
        assertThat(redis.opsForHash().get(buffer.bucketKey(TS), "999999999"))
                .isEqualTo("7");
    }

    @Test
    @DisplayName("조회가 0건이면 버킷을 만들지 않는다 — 없는 pending 에 RENAME 을 걸지 않는다")
    void skipsIntervalWithoutViews() {
        assertThatCode(() -> job.flush(NOW)).doesNotThrowAnyException();

        assertThat(redis.hasKey(buffer.bucketKey(TS))).isFalse();
        assertThat(redis.opsForList().size(buffer.bucketIndexKey())).isZero();
    }

    private long viewCount() {
        entityManager.flush();
        return ((Number) entityManager.createNativeQuery(
                        "select view_count from motif_core.participation_videos where video_seq = ?1")
                .setParameter(1, videoSeq)
                .getSingleResult()).longValue();
    }
}
