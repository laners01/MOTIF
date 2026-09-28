package com.ssafy.motif.coreapi.domain.video.ranking;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * HOT 랭킹 스냅샷 — 실제 Redis 통합 (S15P21D104-130 · 명세 §6.1 · ADR-0015).
 *
 * <p><b>mock 으로는 검증되지 않는 것들이다.</b> 예전 이 테스트는 {@code StringRedisTemplate}
 * 을 mock 해 호출 순서만 봤는데, {@code rankDelta} 가 붙으면서 확인해야 하는 것이
 * <b>Redis 의미론</b>으로 옮겨갔다 — 버전 이력 {@code LINDEX} 의 오프셋이 한 칸씩
 * 어긋나는지, {@code HMGET} 이 없는 field 에 {@code null} 을 주는지, {@code ZSET} 이
 * rank 순으로 돌려주는지. mock 은 이 셋을 전부 통과시켜 놓고 운영에서 틀린다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 *
 * <p>DB 를 쓰지 않지만 {@code @SpringBootTest} 라 컨텍스트가 뜬다. Redis 키는 롤백되지
 * 않으므로 직접 지운다.
 */
@Tag("local-db")
@SpringBootTest
class VideoRankingSnapshotLocalDbTest {

    @Autowired
    private VideoRankingSnapshot snapshot;
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
        Set<String> versions = redis.keys("rank:video:*");
        if (!versions.isEmpty()) {
            redis.delete(versions);
        }
    }

    @Test
    @DisplayName("적재한 순서가 rank 1부터 매겨지고 그 순서대로 읽힌다")
    void storesOrderAsRank() {
        snapshot.replace(List.of(88L, 12L, 7L));

        List<VideoRankingSnapshot.Ranked> page =
                snapshot.page(snapshot.currentVersion(), 0, 10);

        assertThat(page).extracting(VideoRankingSnapshot.Ranked::videoSeq)
                .containsExactly(88L, 12L, 7L);
        assertThat(page).extracting(VideoRankingSnapshot.Ranked::rank)
                .containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("afterRank 다음부터 자른다 — 커서가 이어 받는 자리다")
    void pagesAfterRank() {
        snapshot.replace(List.of(88L, 12L, 7L));

        List<VideoRankingSnapshot.Ranked> page =
                snapshot.page(snapshot.currentVersion(), 1, 10);

        assertThat(page).extracting(VideoRankingSnapshot.Ranked::videoSeq).containsExactly(12L, 7L);
    }

    @Test
    @DisplayName("첫 스냅샷은 전원 0 이다 — null 을 진짜 신규 진입 전용으로 남긴다")
    void firstSnapshotHasZeroDelta() {
        snapshot.replace(List.of(88L, 12L));

        assertThat(snapshot.page(snapshot.currentVersion(), 0, 10))
                .extracting(VideoRankingSnapshot.Ranked::rankDelta)
                .containsExactly(0, 0);
    }

    /**
     * 대비 구간이 3틱이므로 네 번째 적재부터 첫 번째와 견준다. 오프셋이 한 칸이라도
     * 어긋나면 변동값이 통째로 틀리는데, <b>예외는 나지 않는다</b>.
     */
    @Test
    @DisplayName("3틱 전과 견준다 — 상승이 양수, 하락이 음수")
    void comparesAgainstConfiguredTicksAgo() {
        snapshot.replace(List.of(7L, 12L, 88L));   // 기준: 7=1위, 12=2위, 88=3위
        snapshot.replace(List.of(7L, 12L, 88L));
        snapshot.replace(List.of(7L, 12L, 88L));
        snapshot.replace(List.of(88L, 7L, 12L));   // 88: 3위 → 1위, 12: 2위 → 3위

        List<VideoRankingSnapshot.Ranked> page =
                snapshot.page(snapshot.currentVersion(), 0, 10);

        assertThat(page).extracting(
                        VideoRankingSnapshot.Ranked::videoSeq, VideoRankingSnapshot.Ranked::rankDelta)
                .containsExactly(
                        tuple(88L, 2),
                        tuple(7L, -1),
                        tuple(12L, -1));
    }

    @Test
    @DisplayName("대비 버전에 없던 영상은 null 이다 — 신규 진입")
    void newEntryHasNullDelta() {
        snapshot.replace(List.of(7L));
        snapshot.replace(List.of(7L));
        snapshot.replace(List.of(7L));
        snapshot.replace(List.of(7L, 99L));

        assertThat(snapshot.page(snapshot.currentVersion(), 0, 10))
                .extracting(
                        VideoRankingSnapshot.Ranked::videoSeq, VideoRankingSnapshot.Ranked::rankDelta)
                .containsExactly(
                        tuple(7L, 0),
                        tuple(99L, null));
    }

    @Test
    @DisplayName("포인터를 마지막에 옮긴다 — 앞 버전을 읽는 커서는 계속 동작한다")
    void keepsOldVersionReadable() {
        long first = snapshot.replace(List.of(7L, 12L));
        snapshot.replace(List.of(12L, 7L));

        assertThat(snapshot.currentVersion()).isNotEqualTo(first);
        assertThat(snapshot.page(first, 0, 10))
                .extracting(VideoRankingSnapshot.Ranked::videoSeq)
                .containsExactly(7L, 12L);
    }

    @Test
    @DisplayName("버전 키에 TTL 이 걸린다 — 없으면 지난 버전이 무한히 쌓인다")
    void versionKeysExpire() {
        long version = snapshot.replace(List.of(7L));

        assertThat(redis.getExpire("rank:video:" + version)).isPositive();
        assertThat(redis.getExpire("rank:video:" + version + ":delta")).isPositive();
    }

    @Test
    @DisplayName("없는 버전·빈 스냅샷은 모두 빈 목록이다 — 셋을 구분하지 않는다")
    void unknownVersionIsEmpty() {
        assertThat(snapshot.page(1L, 0, 10)).isEmpty();
    }

    @Test
    @DisplayName("빈 목록은 받지 않는다 — clear() 를 써야 '없음' 과 구분된다")
    void rejectsEmptySnapshot() {
        assertThatThrownBy(() -> snapshot.replace(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("clear 는 포인터만 지운다 — 진행 중인 커서는 TTL 이 남은 동안 산다")
    void clearRemovesPointerOnly() {
        long version = snapshot.replace(List.of(7L));

        snapshot.clear();

        assertThat(snapshot.currentVersion()).isNull();
        assertThat(snapshot.page(version, 0, 10)).hasSize(1);
    }
}
