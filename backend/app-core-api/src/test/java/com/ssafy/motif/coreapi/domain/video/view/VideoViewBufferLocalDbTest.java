package com.ssafy.motif.coreapi.domain.video.view;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 조회수 버퍼 — 실제 Redis 통합 (S15P21D104-130 · 명세 §8.5 · ADR-0015).
 *
 * <p>단위 테스트가 닿지 못하는 곳만 본다 — <b>HINCRBY 가 정말 누적되는지</b>와
 * <b>파이프라인이 여러 field 를 한 번에 올리는지</b>. 검증 규칙은
 * {@code VideoViewServiceTest} 가 이미 고정한다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 *
 * <p><b>Redis 키는 롤백되지 않는다.</b> {@code @Transactional} 을 붙여도 DB 만 되돌아가므로
 * 버퍼 키는 {@code @BeforeEach}·{@code @AfterEach} 에서 직접 지운다 — 개발자 로컬에 남으면
 * 다음 실행이 남은 조회수를 보고 엉뚱하게 실패한다({@code ArtistRankingLocalDbTest} 와
 * 같은 규칙).
 */
@Tag("local-db")
@SpringBootTest
class VideoViewBufferLocalDbTest {

    @Autowired
    private VideoViewBuffer buffer;
    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    void setUp() {
        redis.delete(VideoViewBuffer.PENDING);
    }

    @AfterEach
    void tearDown() {
        redis.delete(VideoViewBuffer.PENDING);
    }

    @Test
    @DisplayName("단일 영상 — field 가 생기고 값이 그대로 들어간다")
    void incrementsSingleVideo() {
        buffer.add(Map.of(101L, 3));

        assertThat(pending(101L)).isEqualTo(3);
    }

    @Test
    @DisplayName("여러 영상을 한 번에 — 파이프라인이 전부 반영한다")
    void incrementsManyVideosInOneCall() {
        Map<Long, Integer> views = new LinkedHashMap<>();
        views.put(101L, 3);
        views.put(102L, 1);
        views.put(115L, 2);

        buffer.add(views);

        assertThat(pending(101L)).isEqualTo(3);
        assertThat(pending(102L)).isEqualTo(1);
        assertThat(pending(115L)).isEqualTo(2);
        assertThat(redis.opsForHash().size(VideoViewBuffer.PENDING)).isEqualTo(3);
    }

    @Test
    @DisplayName("같은 영상을 다시 올리면 더해진다 — 덮어쓰지 않는다")
    void accumulatesAcrossCalls() {
        buffer.add(Map.of(101L, 3));
        buffer.add(Map.of(101L, 2));

        assertThat(pending(101L)).isEqualTo(5);
    }

    @Test
    @DisplayName("동시에 올려도 하나도 잃지 않는다 — HINCRBY 가 원자적이다")
    void doesNotLoseConcurrentIncrements() throws Exception {
        int threads = 16;
        int perThread = 50;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    try {
                        // 한꺼번에 풀어 실제로 겹치게 한다 — 순차로 돌면 원자성을 검증하지 못한다
                        start.await();
                        for (int n = 0; n < perThread; n++) {
                            buffer.add(Map.of(101L, 1));
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(pending(101L)).isEqualTo(threads * perThread);
    }

    private long pending(long videoSeq) {
        Object value = redis.opsForHash().get(VideoViewBuffer.PENDING, String.valueOf(videoSeq));
        return value == null ? 0L : Long.parseLong(value.toString());
    }
}
