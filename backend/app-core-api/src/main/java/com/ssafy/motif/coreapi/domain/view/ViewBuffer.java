package com.ssafy.motif.coreapi.domain.view;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 조회수 버퍼 (API 명세 §7.4 · §8.5 · ADR-0015 · ADR-0016) — 조회수 Redis 키를 아는
 * <b>유일한 지점</b>이다.
 *
 * <pre>
 * view:{target}:pending              HASH    field = 대상 seq, value = 아직 DB 에 반영되지 않은 조회수
 * view:{target}:processing:{ts}      HASH    집계 중인 스냅샷 — rotate 로 얼린 것
 * view:{target}:processing:index     LIST    아직 끝나지 않은 {ts} — 재처리 진입점
 * view:{target}:bucket:{ts}          HASH    그 구간에 발생한 조회수          TTL
 * view:{target}:bucket:index         LIST    최신 {ts} 가 앞                 LTRIM
 * view:{target}:flush:{ts}           STRING  DB 반영 멱등 마커               TTL
 * </pre>
 *
 * <p><b>대상마다 인스턴스를 하나씩 둔다</b>({@link ViewConfig} 가 빈 두 개를 만든다).
 * 클래스를 복제하지 않는 이유는 {@link ViewTarget} javadoc 에 있다.
 *
 * <p><b>멱등 마커에 대상이 들어간다.</b> ADR-0015 에서는 {@code view:flush:applied:{ts}} 로
 * 대상이 없었는데, 두 대상이 같은 틱에 같은 {@code {ts}} 로 돌면 먼저 선점한 쪽만 DB 에
 * 반영되고 <b>나머지 하나는 "이미 반영됨" 으로 보고 건너뛴다</b> — 예외도 로그도 없이
 * 조회수가 통째로 사라진다(ADR-0016).
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
 * 랭킹 하나뿐이고 그것만 ZSET 이다. {@code HINCRBY} 는 원자적이라 같은 대상에 동시
 * 요청이 들어와도 서로를 덮지 않는다.
 *
 * <p><b>DB 를 건드리지 않는다.</b> {@code view_count} 반영은 20분 주기 배치의 몫이다 —
 * 조회마다 {@code UPDATE} 를 돌면 인기 대상 한 행에 쓰기가 몰려 행 잠금이 직렬화된다.
 */
@RequiredArgsConstructor
public class ViewBuffer {

    /** 마커는 존재 자체가 기록이다 — Redis 가 빈 값을 허용하지 않아 자리표시자를 둔다. */
    private static final String PLACEHOLDER = "1";

    @Getter
    private final ViewTarget target;
    private final StringRedisTemplate redis;
    private final ViewProperties properties;

    // ── 키 ───────────────────────────────────────────────────────────────────
    // 테스트가 키 모양을 직접 확인하므로 패키지 공개다. 바깥에서 문자열을 조립하지 않는다.

    String pendingKey() {
        return "view:" + target.getKey() + ":pending";
    }

    String processingKey(String ts) {
        return "view:" + target.getKey() + ":processing:" + ts;
    }

    String processingIndexKey() {
        return "view:" + target.getKey() + ":processing:index";
    }

    String bucketKey(String ts) {
        return "view:" + target.getKey() + ":bucket:" + ts;
    }

    String bucketIndexKey() {
        return "view:" + target.getKey() + ":bucket:index";
    }

    String flushMarkerKey(String ts) {
        return "view:" + target.getKey() + ":flush:" + ts;
    }

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
     * @param views {@code 대상 seq → 증가분}. 호출 전에 검증이 끝나 있어야 한다
     */
    public void add(Map<Long, Integer> views) {
        byte[] key = pendingKey().getBytes(StandardCharsets.UTF_8);
        redis.executePipelined((RedisCallback<Object>) connection -> {
            views.forEach((seq, count) -> connection.hashCommands().hIncrBy(
                    key,
                    String.valueOf(seq).getBytes(StandardCharsets.UTF_8),
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
        if (!Boolean.TRUE.equals(redis.hasKey(pendingKey()))) {
            return false;
        }

        redis.opsForList().leftPush(processingIndexKey(), ts);
        if (!Boolean.TRUE.equals(redis.renameIfAbsent(pendingKey(), processingKey(ts)))) {
            redis.opsForList().remove(processingIndexKey(), 1, ts);
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
        List<String> newestFirst = redis.opsForList().range(processingIndexKey(), 0, -1);
        if (newestFirst == null || newestFirst.isEmpty()) {
            return List.of();
        }
        return newestFirst.reversed();
    }

    /** 얼린 스냅샷. 키가 없으면 빈 map — 인덱스만 남은 구간이 여기로 온다. */
    public Map<Long, Long> readProcessing(String ts) {
        Map<Object, Object> raw = redis.opsForHash().entries(processingKey(ts));
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
                .setIfAbsent(flushMarkerKey(ts), PLACEHOLDER, properties.flushMarkerTtl());
        return Boolean.TRUE.equals(first);
    }

    /** 선점을 되돌린다 — DB 반영이 실패했을 때. 풀지 않으면 그 구간이 영영 반영되지 않는다. */
    public void releaseFlush(String ts) {
        redis.delete(flushMarkerKey(ts));
    }

    /**
     * 구간 버킷을 남긴다 — 랭킹의 "최근 / 이전" 재료다.
     *
     * <p><b>{@code HINCRBY} 가 아니라 덮어쓰기다.</b> 입력이 이미 얼린 스냅샷이라 같은
     * {@code ts} 로 두 번 써도 결과가 같아야 한다 — 더하면 재처리가 버킷을 부풀린다.
     * DB 쪽은 마커가 막지만 버킷에는 마커가 없고, <b>덮어쓰기라 필요가 없다.</b>
     *
     * <p>조회가 발생한 대상만 들어간다 — 0을 채워 넣지 않는다.
     *
     * <p>인덱스는 <b>지우고 다시 넣는다</b>. 재처리가 같은 {@code ts} 를 두 번 밀어 넣으면
     * "최근 3개" 가 같은 구간을 두 번 세게 된다.
     */
    public void saveBucket(String ts, Map<Long, Long> counts) {
        if (counts.isEmpty()) {
            return;
        }

        String key = bucketKey(ts);
        Map<String, String> fields = new LinkedHashMap<>();
        counts.forEach((seq, count) -> fields.put(String.valueOf(seq), String.valueOf(count)));

        redis.opsForHash().putAll(key, fields);
        redis.expire(key, properties.bucketTtl());

        redis.opsForList().remove(bucketIndexKey(), 0, ts);
        redis.opsForList().leftPush(bucketIndexKey(), ts);
        redis.opsForList().trim(bucketIndexKey(), 0, properties.bucketKeep() - 1L);
    }

    /**
     * 버킷 구간의 조회수 합 — 랭킹의 "최근 1시간 / 이전 1시간" 재료다.
     *
     * <p><b>구간을 시각으로 계산하지 않는다.</b> 인덱스 LIST 의 앞에서 {@code skip} 개를
     * 건너뛰고 {@code count} 개를 합한다 — 스케줄 드리프트도, 조회가 0건이라 건너뛴
     * 구간도 여기에 영향을 주지 않는다(ADR-0015). 대가로 조회가 전무한 시간대가 있으면
     * "최근 3개" 가 시간상 더 긴 구간을 덮는다.
     *
     * <p>버킷이 아직 {@code skip + count} 개만큼 쌓이지 않았으면 있는 것만 합한다 —
     * 서비스 초기 1시간은 "이전 1시간" 이 전부 0 이고, 그 구간에는 모든 대상의 증감이
     * 같은 규칙으로 양수라 순서가 깨지지 않는다.
     *
     * @param skip  건너뛸 최신 버킷 수 (최근 구간이면 0)
     * @param count 합할 버킷 수
     */
    public Map<Long, Long> sumBuckets(int skip, int count) {
        List<String> timestamps =
                redis.opsForList().range(bucketIndexKey(), skip, (long) skip + count - 1);
        Map<Long, Long> totals = new LinkedHashMap<>();
        if (timestamps == null) {
            return totals;
        }

        for (String ts : timestamps) {
            redis.opsForHash().entries(bucketKey(ts)).forEach((field, value) ->
                    totals.merge(Long.valueOf(field.toString()),
                            Long.valueOf(value.toString()), Long::sum));
        }
        return totals;
    }

    /** 끝난 구간을 치운다 — 스냅샷 키와 미완료 인덱스에서 함께 뺀다. */
    public void discardProcessing(String ts) {
        redis.delete(processingKey(ts));
        redis.opsForList().remove(processingIndexKey(), 0, ts);
    }
}
