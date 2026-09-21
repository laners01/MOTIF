package com.ssafy.motif.coreapi.domain.video.view;

import com.ssafy.motif.common.error.CommonErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 조회 이벤트 접수 (S15P21D104-130 · 명세 §8.5 · ADR-0015).
 *
 * <p><b>Redis 없이 돈다.</b> 여기서 고정하는 것은 검증 규칙과 fail-open 이고, Redis
 * 자체의 동작은 {@code VideoViewBufferLocalDbTest} 가 본다.
 */
class VideoViewServiceTest {

    private static final int MAX_VIDEOS = 3;
    private static final int MAX_COUNT = 100;

    private VideoViewBuffer buffer;
    private VideoViewService service;

    @BeforeEach
    void setUp() {
        buffer = mock(VideoViewBuffer.class);
        // 집계·랭킹 설정은 이 테스트가 쓰지 않는다 — 검증 규칙만 본다
        service = new VideoViewService(buffer, new VideoViewProperties(
                MAX_VIDEOS, MAX_COUNT,
                Duration.ofMinutes(20), Duration.ofHours(6), 12, Duration.ofHours(24), 3,
                new VideoViewProperties.Ranking(500, Duration.ofHours(2), 3, 10,
                        new VideoViewProperties.Weight(0.3, 0.5, 0.2),
                        new VideoViewProperties.Scale(10_000, 300, 3.0))));
    }

    @Test
    @DisplayName("검증을 통과하면 받은 그대로 버퍼에 넘긴다")
    void delegatesToBuffer() {
        Map<Long, Integer> views = Map.of(101L, 3, 102L, 1);

        service.accumulate(views);

        verify(buffer).add(views);
    }

    @Test
    @DisplayName("영상 수가 상한을 넘으면 요청 전체를 거절한다 — 유효한 항목도 적재하지 않는다")
    void rejectsWholeRequestWhenTooManyVideos() {
        Map<Long, Integer> views = new LinkedHashMap<>();
        for (long seq = 1; seq <= MAX_VIDEOS + 1; seq++) {
            views.put(seq, 1);
        }

        assertInvalid(views);
        verify(buffer, never()).add(any());
    }

    @Test
    @DisplayName("상한과 같은 개수는 통과한다 — 경계는 포함이다")
    void acceptsExactlyMaxVideos() {
        Map<Long, Integer> views = new LinkedHashMap<>();
        for (long seq = 1; seq <= MAX_VIDEOS; seq++) {
            views.put(seq, 1);
        }

        assertThatCode(() -> service.accumulate(views)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("count 가 0 이면 거절한다 — 조회가 없었다는 뜻이라 보낼 이유가 없다")
    void rejectsZeroCount() {
        assertInvalid(Map.of(101L, 0));
    }

    @Test
    @DisplayName("count 가 음수면 거절한다 — 누적을 되돌리는 경로는 없다")
    void rejectsNegativeCount() {
        assertInvalid(Map.of(101L, -5));
    }

    @Test
    @DisplayName("count 가 상한을 넘으면 거절한다 — 지금 유일한 어뷰징 방어선이다")
    void rejectsCountAboveMax() {
        assertInvalid(Map.of(101L, MAX_COUNT + 1));

        assertThatCode(() -> service.accumulate(Map.of(101L, MAX_COUNT)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("한 항목만 어긋나도 요청 전체를 거절한다")
    void rejectsWholeRequestOnSingleBadEntry() {
        Map<Long, Integer> views = new LinkedHashMap<>();
        views.put(101L, 3);
        views.put(102L, 0);

        assertInvalid(views);
        verify(buffer, never()).add(any());
    }

    @Test
    @DisplayName("videoSeq 가 0 이하면 거절한다")
    void rejectsNonPositiveVideoSeq() {
        assertInvalid(Map.of(0L, 1));
        assertInvalid(Map.of(-1L, 1));
    }

    @Test
    @DisplayName("count 가 null 이면 거절한다 — {\"101\": null} 이 여기까지 온다")
    void rejectsNullCount() {
        Map<Long, Integer> views = new HashMap<>();
        views.put(101L, null);

        assertInvalid(views);
    }

    @Test
    @DisplayName("videoSeq 가 null 이면 거절한다 — {\"\": 1} 이 여기까지 온다")
    void rejectsNullVideoSeq() {
        Map<Long, Integer> views = new HashMap<>();
        views.put(null, 1);

        assertInvalid(views);
    }

    @Test
    @DisplayName("Redis 가 죽어도 던지지 않는다 — 재생 중인 클라이언트에게 에러를 주지 않는다")
    void swallowsRedisFailure() {
        willThrow(new IllegalStateException("redis down")).given(buffer).add(any());

        assertThatCode(() -> service.accumulate(Map.of(101L, 1)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("검증 실패는 삼키지 않는다 — fail-open 은 Redis 실패에만 적용된다")
    void doesNotSwallowValidationFailure() {
        willThrow(new IllegalStateException("redis down")).given(buffer).add(any());

        assertInvalid(Map.of(101L, 0));
    }

    private void assertInvalid(Map<Long, Integer> views) {
        assertThatThrownBy(() -> service.accumulate(views))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(CommonErrorCode.INVALID_PARAMETER));
    }
}
