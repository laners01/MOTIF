package com.ssafy.motif.notification.consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 멱등 가드 (S15P21D104-312 · 어드민 명세 §4).
 *
 * <p>여기서 보는 것은 <b>반납</b>이다. 선점만 하고 발송에 실패했을 때 키를 지우지
 * 않으면 24시간 동안 재전달이 "이미 처리됨" 으로 조용히 버려진다 — 알림은 영영
 * 안 가는데 로그에는 성공처럼 보인다. 발견이 늦는 종류라 테스트로 박는다.
 *
 * <p>실제 Redis 에 대고 돈다. 인메모리 목으로는 TTL·SETNX 의미가 검증되지 않는다.
 * 실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
class EventIdempotencyGuardLocalDbTest {

    @Autowired
    private EventIdempotencyGuard guard;
    @Autowired
    private StringRedisTemplate redis;

    @Test
    @DisplayName("같은 eventId 는 한 번만 선점된다")
    void claimsOnce() {
        String eventId = UUID.randomUUID().toString();
        try {
            assertThat(guard.claim(eventId)).isTrue();
            assertThat(guard.claim(eventId)).isFalse();
        } finally {
            guard.release(eventId);
        }
    }

    @Test
    @DisplayName("반납하면 다시 선점된다 — 재시도·DLQ 재처리가 막히지 않는다")
    void releaseAllowsRetry() {
        String eventId = UUID.randomUUID().toString();
        try {
            assertThat(guard.claim(eventId)).isTrue();

            guard.release(eventId);

            assertThat(guard.claim(eventId))
                    .as("반납하지 않으면 재전달이 24시간 동안 조용히 버려진다")
                    .isTrue();
        } finally {
            guard.release(eventId);
        }
    }

    @Test
    @DisplayName("선점 키에 TTL 이 붙는다 — 영구 키가 쌓이지 않는다")
    void claimHasTtl() {
        String eventId = UUID.randomUUID().toString();
        try {
            guard.claim(eventId);

            Long ttl = redis.getExpire("evt:" + eventId);
            assertThat(ttl).isNotNull().isPositive();
        } finally {
            guard.release(eventId);
        }
    }
}
