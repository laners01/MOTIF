package com.ssafy.motif.coreapi.domain.user.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * presign 발급 기록 (S15P21D104-280 · 명세 §4.3·§4.4 · ADR-0011).
 *
 * <p><b>Redis 없이 돈다.</b> 여기서 고정하는 것은 키 모양과 TTL 전달이고, Redis 자체의
 * 동작은 검증 대상이 아니다.
 */
class ProfileImagePresignRegistryTest {

    private static final int ME = 7;
    private static final int OTHER = 99;
    private static final String KEY = "profiles/3f9c1a2b.jpg";
    private static final String REDIS_KEY = "profile-image:presign:7:profiles/3f9c1a2b.jpg";

    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private ProfileImagePresignRegistry registry;

    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        given(redis.opsForValue()).willReturn(values);
        registry = new ProfileImagePresignRegistry(redis);
    }

    @Test
    @DisplayName("사용자와 키를 함께 넣고 TTL 을 그대로 건다")
    void remembersWithTtl() {
        registry.remember(ME, KEY, Duration.ofSeconds(300));

        verify(values).set(REDIS_KEY, "1", Duration.ofSeconds(300));
    }

    @Test
    @DisplayName("기록이 있으면 발급된 것이다")
    void issuedWhenPresent() {
        given(redis.hasKey(REDIS_KEY)).willReturn(true);

        assertThat(registry.isIssuedTo(ME, KEY)).isTrue();
    }

    @Test
    @DisplayName("기록이 없으면(만료 포함) 발급되지 않은 것이다")
    void notIssuedWhenAbsent() {
        given(redis.hasKey(REDIS_KEY)).willReturn(false);

        assertThat(registry.isIssuedTo(ME, KEY)).isFalse();
    }

    @Test
    @DisplayName("hasKey 가 null 이어도 false 다 — 발급됨으로 읽으면 검사가 뚫린다")
    void nullHasKeyIsNotIssued() {
        given(redis.hasKey(REDIS_KEY)).willReturn(null);

        assertThat(registry.isIssuedTo(ME, KEY)).isFalse();
    }

    @Test
    @DisplayName("사용자가 다르면 같은 키라도 발급된 것이 아니다 — 이게 §4.4 의 403 근거다")
    void keyIsScopedToUser() {
        given(redis.hasKey(REDIS_KEY)).willReturn(true);

        // 남의 profileImageUrl 은 §4.6 이 공개하므로 키를 아는 것 자체는 막을 수 없다.
        // 막는 것은 "그 사람에게 발급됐는가" 다
        assertThat(registry.isIssuedTo(OTHER, KEY)).isFalse();
    }

    @Test
    @DisplayName("반영이 끝나면 기록을 지운다")
    void forgetsAfterUse() {
        registry.forget(ME, KEY);

        verify(redis).delete(REDIS_KEY);
    }
}
