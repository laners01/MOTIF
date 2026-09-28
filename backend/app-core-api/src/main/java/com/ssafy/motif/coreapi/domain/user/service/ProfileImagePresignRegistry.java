package com.ssafy.motif.coreapi.domain.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 프로필 이미지 presign 발급 기록 (API 명세 §4.3 · §4.4) — ADR-0011.
 *
 * <p><b>무엇을 막는가.</b> §4.4 는 {@code imageKey} 를 그대로 받아 반영한다. 프리픽스만
 * 확인하면 <b>남의 키를 보내도 통과한다</b> — 다른 사용자의 {@code profileImageUrl} 은
 * §4.6 이 공개하므로 키를 알아내는 데 아무 장벽이 없다. 그래서 "이 키를 이 사람에게
 * 발급했다"는 기록이 필요하고, 명세 §4.4 의 403 분기가 그것을 가리킨다.
 *
 * <p><b>수명이 presign URL 과 같다.</b> 업로드 URL 이 만료되면 그 키를 주장할 근거도
 * 함께 사라지는 것이 맞다 — 둘은 같은 사건의 양면이다. TTL 을 걸면 만료 처리를 따로
 * 짤 필요가 없고, 이것이 DB 컬럼 대신 Redis 를 고른 이유다(ADR-0011).
 *
 * <p><b>키는 {@code (userSeq, imageKey)} 쌍이다.</b> 사용자당 한 칸으로 두면 presign 을
 * 두 번 부른 뒤 <b>첫 번째를 업로드했을 때</b> 기록이 덮여 403 이 난다 — 앱이 재시도하면
 * 충분히 생기는 흐름이다. 쌍으로 두면 발급된 것끼리 서로를 무효화하지 않고 TTL 이
 * 알아서 치운다.
 *
 * <p><b>Redis 가 죽으면 §4.3 도 같이 죽는다.</b> 그래서 "발급은 됐는데 반영만 막히는"
 * 상태는 생기지 않는다(fail-closed).
 */
@Component
@RequiredArgsConstructor
public class ProfileImagePresignRegistry {

    private static final String KEY_PREFIX = "profile-image:presign:";

    /** 값은 쓰지 않는다 — 존재 자체가 기록이다. Redis 가 빈 값을 허용하지 않아 자리표시자를 둔다. */
    private static final String PLACEHOLDER = "1";

    private final StringRedisTemplate redis;

    /** 발급을 기록한다. {@code ttl} 은 presigned URL 의 만료와 같은 값이어야 한다. */
    public void remember(Integer userSeq, String imageKey, Duration ttl) {
        redis.opsForValue().set(redisKey(userSeq, imageKey), PLACEHOLDER, ttl);
    }

    /** 이 사람에게 발급된 키인가. 만료됐으면 발급된 적 없는 것과 같게 본다. */
    public boolean isIssuedTo(Integer userSeq, String imageKey) {
        return Boolean.TRUE.equals(redis.hasKey(redisKey(userSeq, imageKey)));
    }

    /**
     * 기록을 지운다 — 반영이 끝나 더 쓸 일이 없을 때.
     *
     * <p>지우지 않아도 TTL 이 치우므로 실패해도 치명적이지 않다. 그래도 지우는 것은
     * 같은 키로 §4.4 를 두 번 부르는 것이 의미 없는 동작이기 때문이다.
     */
    public void forget(Integer userSeq, String imageKey) {
        redis.delete(redisKey(userSeq, imageKey));
    }

    private static String redisKey(Integer userSeq, String imageKey) {
        return KEY_PREFIX + userSeq + ":" + imageKey;
    }
}
