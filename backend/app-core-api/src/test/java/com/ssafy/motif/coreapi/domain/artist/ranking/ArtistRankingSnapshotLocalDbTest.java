package com.ssafy.motif.coreapi.domain.artist.ranking;

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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 아티스트 랭킹 스냅샷 — 실제 Redis 통합 (명세 §5.1 · ADR-0016).
 *
 * <p><b>mock 으로는 검증되지 않는 것들이다.</b> 예전 이 테스트는
 * {@code StringRedisTemplate} 을 mock 해 {@code RENAME} 호출 순서만 봤는데, 버전
 * 스냅샷으로 바뀌면서 확인해야 하는 것이 <b>Redis 의미론</b>으로 옮겨갔다 — 버전 이력
 * {@code LINDEX} 오프셋이 한 칸씩 어긋나는지, {@code HMGET} 이 없는 field 에
 * {@code null} 을 주는지, {@code score = rank} 로 넣은 ZSET 이 rank 순으로 돌아오는지.
 * mock 은 이 셋을 전부 통과시켜 놓고 운영에서 틀린다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 *
 * <p>Redis 키는 롤백되지 않으므로 직접 지운다.
 */
@Tag("local-db")
@SpringBootTest
class ArtistRankingSnapshotLocalDbTest {

    private static final int KARINA = 3;
    private static final int WINTER = 11;
    private static final int NINGNING = 21;

    @Autowired
    private ArtistRankingSnapshot snapshot;
    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    void setUp() {
        clearKeys();
    }

    @AfterEach
    void tearDown() {
        clearKeys();
    }

    private void clearKeys() {
        Set<String> keys = redis.keys("rank:artist:*");
        if (!keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    /** 점수 맵 — 순서를 스냅샷이 확정하는지 보려고 일부러 뒤섞어 넣는다. */
    private static Map<Integer, Double> scores(Map<Integer, Double> given) {
        return new LinkedHashMap<>(given);
    }

    @Test
    @DisplayName("점수 내림차순으로 순위를 확정한다 — 조회는 정렬에 관여하지 않는다")
    void ordersByScoreDescending() {
        snapshot.replace(scores(Map.of(KARINA, 0.3, WINTER, 0.9, NINGNING, 0.5)));

        assertThat(snapshot.read().ranking()).containsExactly(WINTER, NINGNING, KARINA);
    }

    /**
     * 조회·참여가 모두 0인 아티스트가 많아 <b>동점이 대량으로 발생한다</b>. ZSET 정렬에
     * 기대면 {@code artistSeq} DESC 가 나오므로 적재 시점에 확정해야 한다.
     */
    @Test
    @DisplayName("동점이면 artistSeq 오름차순이다 — 역사전순이 나오면 안 된다")
    void breaksTiesByArtistSeqAscending() {
        snapshot.replace(scores(Map.of(NINGNING, 0.0, KARINA, 0.0, WINTER, 0.0)));

        assertThat(snapshot.read().ranking()).containsExactly(KARINA, WINTER, NINGNING);
    }

    @Test
    @DisplayName("첫 스냅샷은 전원 0 이다 — null 을 진짜 신규 아티스트 전용으로 남긴다")
    void firstSnapshotHasZeroDelta() {
        snapshot.replace(scores(Map.of(KARINA, 0.9, WINTER, 0.5)));

        assertThat(snapshot.read().deltas())
                .containsEntry(KARINA, 0)
                .containsEntry(WINTER, 0);
    }

    @Test
    @DisplayName("3틱 전과 견준다 — 상승이 양수, 하락이 음수")
    void comparesAgainstConfiguredTicksAgo() {
        Map<Integer, Double> base = Map.of(KARINA, 0.9, WINTER, 0.5, NINGNING, 0.1);
        snapshot.replace(scores(base));   // 기준: KARINA 1위, WINTER 2위, NINGNING 3위
        snapshot.replace(scores(base));
        snapshot.replace(scores(base));
        // NINGNING 이 3위 → 1위, KARINA 가 1위 → 2위
        snapshot.replace(scores(Map.of(NINGNING, 0.99, KARINA, 0.9, WINTER, 0.5)));

        ArtistRankingSnapshot.Snapshot current = snapshot.read();

        assertThat(current.ranking()).containsExactly(NINGNING, KARINA, WINTER);
        // WINTER 도 2위 → 3위로 한 칸 내려갔다
        assertThat(current.deltas())
                .containsEntry(NINGNING, 2)
                .containsEntry(KARINA, -1)
                .containsEntry(WINTER, -1);
    }

    @Test
    @DisplayName("대비 버전에 없던 아티스트는 null 이다 — 신규 진입")
    void newEntryHasNullDelta() {
        Map<Integer, Double> base = Map.of(KARINA, 0.9);
        snapshot.replace(scores(base));
        snapshot.replace(scores(base));
        snapshot.replace(scores(base));
        snapshot.replace(scores(Map.of(KARINA, 0.9, WINTER, 0.1)));

        ArtistRankingSnapshot.Snapshot current = snapshot.read();

        assertThat(current.deltas()).containsEntry(KARINA, 0);
        assertThat(current.deltas()).doesNotContainKey(WINTER);
    }

    @Test
    @DisplayName("버전 키에 TTL 이 걸린다 — 없으면 지난 버전이 무한히 쌓인다")
    void versionKeysExpire() {
        Long version = snapshot.replace(scores(Map.of(KARINA, 0.9)));

        assertThat(redis.getExpire("rank:artist:" + version)).isPositive();
        assertThat(redis.getExpire("rank:artist:" + version + ":delta")).isPositive();
    }

    @Test
    @DisplayName("아티스트가 0명이면 포인터를 지운다 — 빈 스냅샷을 가리키지 않는다")
    void emptyScoresClearPointer() {
        snapshot.replace(scores(Map.of(KARINA, 0.9)));

        assertThat(snapshot.replace(Map.of())).isNull();
        assertThat(snapshot.currentVersion()).isNull();
        assertThat(snapshot.read().isEmpty()).isTrue();
    }

    @Test
    @DisplayName("포인터를 마지막에 옮긴다 — 적재 중에는 옛 버전이 보인다")
    void pointerMovesLast() {
        Long first = snapshot.replace(scores(Map.of(KARINA, 0.9, WINTER, 0.5)));
        Long second = snapshot.replace(scores(Map.of(WINTER, 0.9, KARINA, 0.5)));

        assertThat(second).isGreaterThan(first);
        assertThat(snapshot.currentVersion()).isEqualTo(second);
        assertThat(snapshot.read().ranking()).containsExactly(WINTER, KARINA);
    }
}
