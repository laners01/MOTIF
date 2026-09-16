package com.ssafy.motif.coreapi.domain.artist.ranking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 랭킹 스냅샷 (명세 §5.1 · S15P21D104-51).
 *
 * <p><b>Redis 없이 돈다</b> ({@code ProfileImagePresignRegistryTest} 와 같은 방식) —
 * 여기서 고정하는 것은 키 이름·정렬 규칙·교체 순서이고, Redis 자체의 동작은 검증 대상이
 * 아니다.
 */
class ArtistRankingSnapshotTest {

    private StringRedisTemplate redis;
    private ZSetOperations<String, String> zset;
    private ArtistRankingSnapshot snapshot;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        zset = mock(ZSetOperations.class);
        given(redis.opsForZSet()).willReturn(zset);
        snapshot = new ArtistRankingSnapshot(redis);
    }

    /** Redis 가 주는 순서를 흉내 낸다 — {@code rangeWithScores} 는 점수 <b>오름차순</b>이다. */
    @SafeVarargs
    private void givenSnapshot(String key, TypedTuple<String>... tuples) {
        given(zset.rangeWithScores(key, 0, -1)).willReturn(new LinkedHashSet<>(List.of(tuples)));
    }

    private static TypedTuple<String> entry(int artistSeq, double score) {
        return TypedTuple.of(String.valueOf(artistSeq), score);
    }

    @Test
    @DisplayName("점수 내림차순으로 정렬한다")
    void sortsByScoreDesc() {
        givenSnapshot("rank:artist:current",
                entry(9, 10.0), entry(3, 500.0), entry(7, 120.0));

        assertThat(snapshot.current()).containsExactly(3, 7, 9);
    }

    @Test
    @DisplayName("동점이면 artistSeq 오름차순이다 — ZSET 의 역사전순에 기대지 않는다")
    void breaksTiesByArtistSeqAscending() {
        // 지금 운영 상태가 정확히 이 모양이다 — 조회·참여가 0이라 전원 동점이다.
        // ZREVRANGE 에 맡기면 동점일 때 member 역사전순이라 9, 7, 3 이 나온다.
        givenSnapshot("rank:artist:current",
                entry(9, 0.0), entry(3, 0.0), entry(7, 0.0));

        assertThat(snapshot.current()).containsExactly(3, 7, 9);
    }

    @Test
    @DisplayName("비어 있으면 빈 목록이다 — 예외가 아니다")
    void emptySnapshotIsEmptyList() {
        given(zset.rangeWithScores(anyString(), eq(0L), eq(-1L))).willReturn(Set.of());

        assertThat(snapshot.current()).isEmpty();
        assertThat(snapshot.previousRanks()).isEmpty();
    }

    @Test
    @DisplayName("직전 순위는 1-base map 이다 — 신규 진입을 '없음' 으로 구분할 수 있어야 한다")
    void previousRanksAreOneBased() {
        givenSnapshot("rank:artist:prev", entry(3, 500.0), entry(7, 120.0));

        assertThat(snapshot.previousRanks())
                .containsEntry(3, 1)
                .containsEntry(7, 2)
                .doesNotContainKey(9);
    }

    @Test
    @DisplayName("임시 키에 적재한 뒤 RENAME 으로 갈아끼운다 — 반쯤 찬 ZSET 을 노출하지 않는다")
    void replacesViaRename() {
        snapshot.replaceCurrent(Map.of(3, 500.0, 7, 120.0));

        InOrder order = inOrder(redis, zset);
        order.verify(redis).delete("rank:artist:current:building");
        order.verify(zset).add(eq("rank:artist:current:building"), any());
        order.verify(redis).rename("rank:artist:current:building", "rank:artist:current");
    }

    @Test
    @DisplayName("점수가 비면 RENAME 대신 DELETE 다 — Redis 에 빈 키는 없어 RENAME 이 터진다")
    void deletesWhenNoArtists() {
        snapshot.replaceCurrent(Map.of());

        verify(redis).delete("rank:artist:current");
        verify(redis, never()).rename(anyString(), anyString());
        verify(zset, never()).add(anyString(), any());
    }

    @Test
    @DisplayName("prev 는 별도 키다 — current 를 건드리지 않는다")
    void previousUsesItsOwnKey() {
        snapshot.replacePrevious(Map.of(3, 500.0));

        verify(redis).rename("rank:artist:prev:building", "rank:artist:prev");
        verify(redis, never()).rename(anyString(), eq("rank:artist:current"));
    }
}
