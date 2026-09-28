package com.ssafy.motif.notification.consumer;

import com.ssafy.motif.notification.global.config.NotificationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 중복 소비 방어 — {@code SETNX evt:{eventId}} TTL 24h (어드민 명세 §4, ADR-0011).
 *
 * <p><b>왜 DB 가 아니라 Redis 인가.</b> 원래 설계는 {@code notification_log} 유니크 제약
 * 이었는데 {@code notifications} 테이블을 두지 않기로 하면서(§14) <b>"이미 보냈다" 를
 * 적을 행이 어디에도 없어졌다.</b> ADR-0005 가 제시한 두 방식 중 DB 유니크가 사라져
 * Redis SETNX 만 남는다. 만료가 데이터의 본질이라 ADR-0011 기준에도 맞는다.
 *
 * <p><b>왜 방어가 필요한가.</b> RabbitMQ 는 at-least-once 다 — 컨슈머가 ack 전에 죽으면
 * 재전달되고, 리스너 재시도도 3회 걸려 있다({@code MqConfig}). 막지 않으면 같은 푸시가
 * 두 번 간다. 사용자 눈에 바로 보이는 고장이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventIdempotencyGuard {

    private static final String KEY_PREFIX = "evt:";

    private final StringRedisTemplate redis;
    private final NotificationProperties properties;

    /**
     * 이 이벤트를 처음 보는가. {@code true} 면 처리 권리를 선점한 것이다.
     *
     * <p><b>선점은 발송 전에 한다.</b> 발송 후에 찍으면 그 사이 도착한 재전달이 같은
     * 푸시를 한 번 더 보낸다.
     */
    public boolean claim(String eventId) {
        Boolean first = redis.opsForValue()
                .setIfAbsent(KEY_PREFIX + eventId, "1", properties.dedupTtl());
        return Boolean.TRUE.equals(first);
    }

    /**
     * 선점을 되돌린다 — 처리 중 실패했을 때.
     *
     * <p>이게 없으면 <b>재시도와 DLQ 재처리가 통째로 막힌다.</b> 선점만 해놓고 발송에
     * 실패하면 키는 24시간 남아 있어서, 재전달된 메시지가 "이미 처리됨" 으로 조용히
     * 버려진다 — 알림이 영영 안 가고 로그에는 성공처럼 보인다.
     */
    public void release(String eventId) {
        redis.delete(KEY_PREFIX + eventId);
    }
}
