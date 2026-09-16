package com.ssafy.motif.coreapi.domain.video.ranking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * HOT 랭킹 스냅샷 (명세 §6.1 · S15P21D104-113).
 *
 * <p><b>Redis 없이 돈다</b>({@code ArtistRankingSnapshotTest} 와 같은 방식) — 여기서
 * 고정하는 것은 키 구조·적재 순서·rank 표현이고 Redis 자체의 동작은 검증 대상이 아니다.
 */
class VideoRankingSnapshotTest {

    private static final Duration RETAIN = Duration.ofMinutes(15);

    private StringRedisTemplate redis;
    private ZSetOperations<String, String> zset;
    private ValueOperations<String, String> values;
    private VideoRankingSnapshot snapshot;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        zset = mock(ZSetOperations.class);
        values = mock(ValueOperations.class);
        given(redis.opsForZSet()).willReturn(zset);
        given(redis.opsForValue()).willReturn(values);
        snapshot = new VideoRankingSnapshot(redis,
                new VideoRankingProperties(Duration.ofDays(2), Duration.ofMinutes(5), RETAIN));
    }

    @Test
    @DisplayName("순서를 rank 점수로 적재한다 — 1위가 1점이다")
    @SuppressWarnings("unchecked")
    void storesOrderAsRankScores() {
        long version = snapshot.replace(List.of(88L, 12L, 7L));

        ArgumentCaptor<Set<TypedTuple<String>>> captor = ArgumentCaptor.forClass(Set.class);
        verify(zset).add(eq("rank:video:" + version), captor.capture());

        assertThat(captor.getValue())
                .extracting(TypedTuple::getValue, TypedTuple::getScore)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("88", 1.0),
                        org.assertj.core.groups.Tuple.tuple("12", 2.0),
                        org.assertj.core.groups.Tuple.tuple("7", 3.0));
    }

    @Test
    @DisplayName("포인터를 맨 마지막에 옮긴다 — 그 전까지 새 버전은 아무도 모른다")
    void movesPointerLast() {
        long version = snapshot.replace(List.of(88L));

        InOrder order = inOrder(zset, redis, values);
        order.verify(zset).add(eq("rank:video:" + version), any());
        order.verify(redis).expire("rank:video:" + version, RETAIN);
        order.verify(values).set("rank:video:current", String.valueOf(version));
    }

    @Test
    @DisplayName("빈 목록은 거부한다 — 후보 0건은 clear() 로 다룬다")
    void rejectsEmptySnapshot() {
        assertThatThrownBy(() -> snapshot.replace(List.of()))
                .isInstanceOf(IllegalArgumentException.class);

        verify(zset, never()).add(anyString(), any());
        verify(values, never()).set(anyString(), anyString());
    }

    @Test
    @DisplayName("clear 는 포인터만 지운다 — 진행 중인 커서의 지난 버전은 남긴다")
    void clearRemovesPointerOnly() {
        snapshot.clear();

        verify(redis).delete("rank:video:current");
        verify(redis, never()).delete(org.mockito.ArgumentMatchers.<java.util.Collection<String>>any());
    }

    @Test
    @DisplayName("포인터가 없으면 현재 버전이 null 이다")
    void noPointerMeansNoVersion() {
        given(values.get("rank:video:current")).willReturn(null);

        assertThat(snapshot.currentVersion()).isNull();
    }

    @Test
    @DisplayName("포인터 값이 깨져 있어도 500 이 아니라 '없음' 이다")
    void brokenPointerMeansNoVersion() {
        given(values.get("rank:video:current")).willReturn("nonsense");

        assertThat(snapshot.currentVersion()).isNull();
    }

    @Test
    @DisplayName("afterRank 다음 순위부터 자른다 — 배타 하한이다")
    void pagesAfterGivenRank() {
        given(zset.rangeByScoreWithScores("rank:video:100", 11.0, Double.POSITIVE_INFINITY, 0, 2))
                .willReturn(new LinkedHashSet<>(List.of(
                        TypedTuple.of("55", 11.0), TypedTuple.of("66", 12.0))));

        assertThat(snapshot.page(100L, 10, 2))
                .containsExactly(
                        new VideoRankingSnapshot.Ranked(11, 55L),
                        new VideoRankingSnapshot.Ranked(12, 66L));
    }

    @Test
    @DisplayName("만료된 버전·없는 버전은 빈 목록이다 — 예외가 아니다")
    void missingVersionIsEmptyPage() {
        given(zset.rangeByScoreWithScores(anyString(), anyDouble(), anyDouble(), anyLong(), anyLong()))
                .willReturn(Set.of());

        assertThat(snapshot.page(999L, 0, 10)).isEmpty();
    }
}
