package com.ssafy.motif.coreapi.domain.video.view;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 조회수 버퍼 (API 명세 §8.5 · ADR-0015) — 조회수 Redis 키를 아는 <b>유일한 지점</b>이다.
 *
 * <pre>
 * view:video:pending              HASH    field = videoSeq, value = 아직 DB 에 반영되지 않은 조회수
 * view:video:processing:{ts}      HASH    집계 중인 스냅샷 — rotate 로 얼린 것
 * view:video:processing:index     LIST    아직 끝나지 않은 {ts} — 재처리 진입점
 * view:video:bucket:{ts}          HASH    그 구간에 발생한 조회수          TTL
 * view:video:bucket:index         LIST    최신 {ts} 가 앞                 LTRIM
 * view:flush:applied:{ts}         STRING  DB 반영 멱등 마커               TTL
 * </pre>
 *
 * <p><b>인덱스 키가 {@code :{ts}} 자리와 이름 공간을 나눠 쓴다.</b> {@code {ts}} 는 항상
 * 숫자라 실제로 부딪히지 않고, <b>이 클래스는 프리픽스를 glob 하지 않는다</b> — 미완료
 * 구간을 {@code SCAN} 이 아니라 인덱스 LIST 로 찾는 것이 설계의 요점이라(ADR-0015)
 * 패턴 조회가 아예 없다.
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

    static final String PROCESSING_PREFIX = "view:video:processing:";
    static final String PROCESSING_INDEX = "view:video:processing:index";
    static final String BUCKET_PREFIX = "view:video:bucket:";
    static final String BUCKET_INDEX = "view:video:bucket:index";
    static final String FLUSH_MARKER_PREFIX = "view:flush:applied:";

    /** 마커는 존재 자체가 기록이다 — Redis 가 빈 값을 허용하지 않아 자리표시자를 둔다. */
    private static final String PLACEHOLDER = "1";

    private final StringRedisTemplate redis;
    private final VideoViewProperties properties;

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

    /**
     * 누적분을 {@code processing:{ts}} 로 얼린다 — 이 순간부터 새 조회수는 새
     * {@code pending} 에 쌓인다.
     *
     * <p><b>{@code RENAME} 이 아니라 {@code RENAMENX} 다.</b> {@code RENAME} 은 대상 키가
     * 이미 있으면 덮어쓴다 — 재처리가 밀려 같은 {@code ts} 의 {@code processing} 이 남아
     * 있는 상태에서 rotate 하면 <b>그 안의 조회수가 통째로 사라진다</b>. 대상이 있으면
     * 옮기지 않고 그쪽을 먼저 처리하게 둔다.
     *
     * <p><b>키가 없으면 아무것도 하지 않는다.</b> 그 주기에 조회가 0건이면 Redis 에
     * {@code pending} 키 자체가 없고, 없는 키에 {@code RENAME} 계열을 걸면 예외가 난다
     * (§5.1 {@code ArtistRankingSnapshot} 이 같은 함정을 밟았다). 확인과 rename 사이에 새
     * 조회가 들어오는 창이 있지만, 그 증분은 다음 주기로 넘어갈 뿐 사라지지 않는다.
     *
     * <p><b>인덱스를 먼저 넣는다.</b> 키만 옮겨지고 인덱스가 없으면 그 구간은 재처리
     * 대상에서 <b>영영 빠져</b> 조회수가 사라진다. 반대로 인덱스만 남는 것은 무해하다 —
     * 다음 실행이 빈 키로 보고 정리한다.
     *
     * @return 옮겼으면 {@code true}. 조회가 없었거나 앞 구간이 남아 있으면 {@code false}
     */
    public boolean rotate(String ts) {
        if (!Boolean.TRUE.equals(redis.hasKey(PENDING))) {
            return false;
        }

        redis.opsForList().leftPush(PROCESSING_INDEX, ts);
        if (!Boolean.TRUE.equals(redis.renameIfAbsent(PENDING, PROCESSING_PREFIX + ts))) {
            redis.opsForList().remove(PROCESSING_INDEX, 1, ts);
            return false;
        }
        return true;
    }

    /**
     * 아직 끝나지 않은 구간 — <b>오래된 것부터</b>.
     *
     * <p>{@code SCAN} 을 쓰지 않는다. 패턴 조회는 순서를 주지 않고 운영에서 피하는
     * 명령이라, 미완료 구간을 명시적인 리스트로 들고 있는다(ADR-0015).
     */
    public List<String> unfinished() {
        List<String> newestFirst = redis.opsForList().range(PROCESSING_INDEX, 0, -1);
        if (newestFirst == null || newestFirst.isEmpty()) {
            return List.of();
        }
        return newestFirst.reversed();
    }

    /** 얼린 스냅샷. 키가 없으면 빈 map — 인덱스만 남은 구간이 여기로 온다. */
    public Map<Long, Long> readProcessing(String ts) {
        Map<Object, Object> raw = redis.opsForHash().entries(PROCESSING_PREFIX + ts);
        Map<Long, Long> counts = new LinkedHashMap<>();
        raw.forEach((field, value) ->
                counts.put(Long.valueOf(field.toString()), Long.valueOf(value.toString())));
        return counts;
    }

    /**
     * DB 반영 권리를 선점한다 — {@code true} 면 <b>아직 반영되지 않은</b> 구간이다.
     *
     * <p>DB 와 Redis 를 한 트랜잭션에 묶을 수 없으므로, "여기까지 했다" 를 적는 자리가
     * 따로 필요하다. 이 마커가 없으면 <b>DB 반영 성공 → Redis 후처리 전 종료</b> 에서
     * 재처리가 같은 조회수를 한 번 더 더한다.
     *
     * <p>{@code EventIdempotencyGuard} 와 같은 {@code SETNX} 패턴이지만 <b>되돌리지
     * 않는다</b>. 그쪽은 선점 후 발송에 실패하면 풀어 줘야 재전달이 살아나는데, 여기서는
     * 선점 직후 같은 호출에서 DB 문장이 돌고 그 문장이 실패하면 예외가 나가 마커가
     * 남는다 — 남은 마커는 "반영됐다" 는 거짓이 되므로 <b>DB 실패 시에는 호출부가 푼다</b>.
     */
    public boolean claimFlush(String ts) {
        Boolean first = redis.opsForValue()
                .setIfAbsent(FLUSH_MARKER_PREFIX + ts, PLACEHOLDER, properties.flushMarkerTtl());
        return Boolean.TRUE.equals(first);
    }

    /** 선점을 되돌린다 — DB 반영이 실패했을 때. 풀지 않으면 그 구간이 영영 반영되지 않는다. */
    public void releaseFlush(String ts) {
        redis.delete(FLUSH_MARKER_PREFIX + ts);
    }

    /**
     * 구간 버킷을 남긴다 — 랭킹의 "최근 1시간 / 이전 1시간" 재료다.
     *
     * <p><b>{@code HINCRBY} 가 아니라 덮어쓰기다.</b> 입력이 이미 얼린 스냅샷이라 같은
     * {@code ts} 로 두 번 써도 결과가 같아야 한다 — 더하면 재처리가 버킷을 부풀린다.
     * DB 쪽은 마커가 막지만 버킷에는 마커가 없고, <b>덮어쓰기라 필요가 없다.</b>
     *
     * <p>조회가 발생한 영상만 들어간다 — 0을 채워 넣지 않는다.
     *
     * <p>인덱스는 <b>지우고 다시 넣는다</b>. 재처리가 같은 {@code ts} 를 두 번 밀어 넣으면
     * "최근 3개" 가 같은 구간을 두 번 세게 된다.
     */
    public void saveBucket(String ts, Map<Long, Long> counts) {
        if (counts.isEmpty()) {
            return;
        }

        String key = BUCKET_PREFIX + ts;
        Map<String, String> fields = new LinkedHashMap<>();
        counts.forEach((videoSeq, count) ->
                fields.put(String.valueOf(videoSeq), String.valueOf(count)));

        redis.opsForHash().putAll(key, fields);
        redis.expire(key, properties.bucketTtl());

        redis.opsForList().remove(BUCKET_INDEX, 0, ts);
        redis.opsForList().leftPush(BUCKET_INDEX, ts);
        redis.opsForList().trim(BUCKET_INDEX, 0, properties.bucketKeep() - 1L);
    }

    /** 끝난 구간을 치운다 — 스냅샷 키와 미완료 인덱스에서 함께 뺀다. */
    public void discardProcessing(String ts) {
        redis.delete(PROCESSING_PREFIX + ts);
        redis.opsForList().remove(PROCESSING_INDEX, 0, ts);
    }
}
