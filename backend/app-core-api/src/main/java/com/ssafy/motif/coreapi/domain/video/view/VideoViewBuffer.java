package com.ssafy.motif.coreapi.domain.video.view;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 조회수 버퍼 (API 명세 §8.5 · ADR-0015) — 조회수 Redis 키를 아는 <b>유일한 지점</b>이다.
 *
 * <pre>
 * view:video:pending   HASH   field = videoSeq, value = 아직 DB 에 반영되지 않은 조회수
 * </pre>
 *
 * <p>키 이름과 field 표현을 여기만 안다. API 와 집계 배치가 각자 문자열을 들고 있으면
 * 이름을 바꿀 때 한쪽만 고쳐져 <b>예외도 로그도 없이 집계가 멈춘다</b>
 * ({@code VideoRankingSnapshot} 과 같은 규칙).
 *
 * <p><b>ZSET 이 아니라 HASH 다.</b> 누적에는 정렬이 필요 없다 — 정렬이 필요한 것은 최종
 * 랭킹 하나뿐이고 그것만 ZSET 이다. {@code HINCRBY} 는 원자적이라 같은 영상에 동시
 * 요청이 들어와도 서로를 덮지 않는다.
 *
 * <p><b>DB 를 건드리지 않는다.</b> {@code participation_videos.view_count} 반영은 20분
 * 주기 배치의 몫이다 — 조회마다 {@code UPDATE} 를 돌면 인기 영상 한 행에 쓰기가 몰려
 * 행 잠금이 직렬화된다(ADR-0015).
 */
@Component
@RequiredArgsConstructor
public class VideoViewBuffer {

    /** 아직 집계되지 않은 조회수. 배치가 통째로 가져간다. */
    static final String PENDING = "view:video:pending";

    private final StringRedisTemplate redis;

    /**
     * 조회수를 누적한다.
     *
     * <p><b>파이프라인으로 한 번에 보낸다.</b> 항목마다 왕복하면 50개짜리 요청에 왕복이
     * 50번이다 — 조회 이벤트는 재생 중에 오는 호출이라 그 지연이 그대로 사용자 쪽에 앉는다.
     * 원자성 때문이 아니다({@code HINCRBY} 하나하나가 이미 원자적이다). 파이프라인은
     * 트랜잭션이 아니므로 중간에 끊기면 일부만 반영될 수 있는데, 조회수는 그 차이를
     * 다음 요청이 메운다.
     *
     * <p>{@link StringRedisTemplate} 의 직렬화가 UTF-8 문자열이므로 raw 명령에도 같은
     * 인코딩으로 넘긴다 — 다른 인코딩을 쓰면 배치가 읽는 field 와 어긋난다.
     *
     * @param views {@code videoSeq → 증가분}. 호출 전에 검증이 끝나 있어야 한다
     */
    public void add(Map<Long, Integer> views) {
        byte[] key = PENDING.getBytes(StandardCharsets.UTF_8);
        redis.executePipelined((RedisCallback<Object>) connection -> {
            views.forEach((videoSeq, count) -> connection.hashCommands().hIncrBy(
                    key,
                    String.valueOf(videoSeq).getBytes(StandardCharsets.UTF_8),
                    count));
            // 파이프라인 콜백은 null 을 돌려줘야 한다 — 값을 돌려주면 템플릿이 막는다
            return null;
        });
    }
}
