package com.ssafy.motif.coreapi.domain.video.ranking;

import com.ssafy.motif.coreapi.domain.video.view.VideoViewProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * HOT 참여 영상 랭킹 스냅샷 (API 명세 §6.1) — 랭킹 Redis 키를 아는 <b>유일한 지점</b>이다.
 *
 * <p>키 이름과 member 표현을 여기만 안다. 조회 서비스와 배치가 각자 키 문자열을 들고
 * 있으면 이름을 바꿀 때 한쪽만 고쳐져 <b>예외도 로그도 없이 빈 랭킹</b>이 된다.
 *
 * <pre>
 * rank:video:current           STRING  현재 버전 값 (포인터)
 * rank:video:{version}         ZSET    score = rank(1-base), member = videoSeq
 * rank:video:{version}:delta   HASH    field = videoSeq, value = rankDelta
 * rank:video:versions          LIST    최신 version 이 앞 — rankDelta 대비 대상을 찾는다
 * </pre>
 *
 * <p><b>왜 버전이 있는가.</b> §6.1 은 커서 페이지네이션이고 커서에 버전이 박힌다 —
 * 배치가 순위를 갈아도 진행 중인 스크롤은 같은 순서를 계속 페이지해야 한다(§1.9 구현
 * 노트). 그래서 §5.1 처럼 키 하나를 덮어쓰는 방식을 쓸 수 없고, 갱신마다 새 키를 만들고
 * 포인터를 옮긴다.
 *
 * <p><b>정렬을 ZSET 에 맡기지 않는다 — score 가 점수가 아니라 rank 다.</b> 점수를 넣고
 * {@code ZREVRANGE} 로 읽으면 동점일 때 member 를 <b>역사전순</b>으로 주는데(§5.1 이
 * 겪은 함정) 커서 페이징에서는 페이지 경계마다 순서가 흔들린다. 순서는 배치가
 * {@code score DESC → videoSeq ASC} 로 확정해 rank 로 못박고, 읽을 때는 rank 구간만 자른다.
 *
 * <p><b>{@code rankDelta} 를 배치가 계산해 둔다</b> *(2026-09-21 — ADR-0015)*. 조회 시점에
 * 이전 버전을 읽어 계산하면 <b>요청마다</b> 스냅샷 전량을 읽게 된다.
 *
 * <p><b>{@code retain} 이 두 가지를 겸한다.</b> 커서 시퀀스의 유효 기간이면서 동시에
 * {@code rankDelta} 대비 버전의 보존 기간이다. {@code interval × rankDeltaBuckets} 보다
 * 짧으면 대비 대상이 언제나 만료돼 있어 <b>순위 변동이 조용히 전원 {@code null}</b> 이
 * 된다 — 예외도 로그도 남지 않는다.
 */
@Component
@RequiredArgsConstructor
public class VideoRankingSnapshot {

    /** 현재 버전 포인터. */
    static final String POINTER = "rank:video:current";

    /** 버전 이력 — {@code rankDelta} 의 대비 대상을 여기서 찾는다. */
    static final String VERSIONS = "rank:video:versions";

    private static final String VERSION_PREFIX = "rank:video:";
    private static final String DELTA_SUFFIX = ":delta";

    private final StringRedisTemplate redis;
    private final VideoViewProperties properties;

    /**
     * 확정된 순서를 새 버전으로 적재하고 포인터를 옮긴다.
     *
     * <p><b>빈 목록은 받지 않는다</b> — {@link #clear()} 를 쓴다. Redis 에 빈 키는
     * 존재하지 않아 ZADD 가 아무것도 만들지 않고, 그 상태로 포인터를 옮기면 "있는
     * 버전을 가리키는데 비어 있다" 와 "버전이 없다" 가 구분되지 않는다.
     *
     * <p><b>순서가 중요하다.</b> 적재 → TTL → 버전 이력 → 포인터다. 포인터를 마지막에
     * 옮기므로 그 전까지 이 버전은 아무도 모른다 — §5.1 의 {@code :building} +
     * {@code RENAME} 이 필요 없는 이유다.
     *
     * @param ordered rank 1부터의 {@code videoSeq} 목록 (배치가 확정한 순서)
     * @return 적재한 버전 — 로그·테스트용
     */
    public long replace(List<Long> ordered) {
        if (ordered.isEmpty()) {
            throw new IllegalArgumentException("빈 스냅샷은 clear() 로 지운다");
        }

        // 이력을 밀기 전에 읽는다 — 밀고 나면 인덱스가 한 칸씩 어긋난다
        Map<Long, Integer> base = baseRanks();

        long version = nextVersion();
        String key = versionKey(version);
        String deltaKey = key + DELTA_SUFFIX;
        // 버전이 단조 증가라 정상적으로는 빈 키다. 사람이 손으로 만든 키가 남아 있는
        // 경우까지 덮는다
        redis.delete(List.of(key, deltaKey));

        Set<TypedTuple<String>> tuples = new LinkedHashSet<>();
        Map<String, String> deltas = new LinkedHashMap<>();
        for (int i = 0; i < ordered.size(); i++) {
            Long videoSeq = ordered.get(i);
            int rank = i + 1;
            tuples.add(TypedTuple.of(String.valueOf(videoSeq), (double) rank));

            Integer previousRank = base.get(videoSeq);
            if (previousRank != null) {
                // 상승이 양수다 — 5위에서 3위면 +2 (§5.1 과 같은 규약)
                deltas.put(String.valueOf(videoSeq), String.valueOf(previousRank - rank));
            } else if (base.isEmpty()) {
                // 대비할 버전이 아예 없는 첫 틱이다. 전원 0 으로 심어야 null 이 진짜
                // 신규 진입 전용으로 남는다 (§5.1 과 같은 판단)
                deltas.put(String.valueOf(videoSeq), "0");
            }
            // 대비 버전은 있는데 거기 없던 영상 — field 를 넣지 않는다(= null, 신규 진입)
        }

        redis.opsForZSet().add(key, tuples);
        redis.opsForHash().putAll(deltaKey, deltas);
        redis.expire(key, properties.ranking().retain());
        redis.expire(deltaKey, properties.ranking().retain());

        redis.opsForList().leftPush(VERSIONS, String.valueOf(version));
        redis.opsForList().trim(VERSIONS, 0, properties.ranking().rankDeltaBuckets() + 1L);

        // 포인터를 마지막에 옮긴다 — 그 전까지 이 버전은 아무도 모른다
        redis.opsForValue().set(POINTER, String.valueOf(version));
        return version;
    }

    /**
     * 포인터를 지운다 — 후보가 0건일 때.
     *
     * <p>지난 버전 키와 이력은 건드리지 않는다. 진행 중인 커서 시퀀스는 TTL 이 남은
     * 동안 계속 동작한다 — 새 진입만 빈 목록이 된다.
     */
    public void clear() {
        redis.delete(POINTER);
    }

    /** 현재 버전. 포인터가 없거나 값이 깨져 있으면 {@code null}(= 스냅샷 없음). */
    public Long currentVersion() {
        return parseVersion(redis.opsForValue().get(POINTER));
    }

    /** 스냅샷 한 자리 — 순위와 영상, 그리고 직전 대비 변동. */
    public record Ranked(int rank, Long videoSeq, Integer rankDelta) {
    }

    /**
     * rank 구간 한 조각 — {@code afterRank} <b>다음</b> 순위부터 {@code limit} 건.
     *
     * <p>만료된 버전·없는 버전·빈 스냅샷이 모두 <b>빈 목록</b>이다. 셋을 구분할 이유가
     * 없다 — §6.1 은 세 경우 모두 "빈 목록 + {@code hasNext:false}" 로 답한다.
     *
     * <p>rank 가 정수라 {@code afterRank + 1} 이상으로 잡으면 배타 하한과 같다.
     * {@code rangeByScoreWithScores} 는 score 오름차순, 즉 rank 순으로 돌려준다.
     *
     * <p><b>순위를 점수째로 돌려준다</b> — 호출부가 "{@code afterRank + 몇 번째}" 로
     * 다시 세면 적재가 1부터 빈틈없이 매긴다는 사실에 몰래 기대게 된다. 그 가정이
     * 깨지는 날 순위만 어긋나고 예외는 나지 않는다.
     *
     * <p>{@code rankDelta} 는 이 페이지 분만 {@code HMGET} 한다 — 항목마다 물으면 한
     * 페이지에 그만큼 왕복이 늘고, 전량을 읽으면 스냅샷 크기에 비례한다.
     *
     * @param afterRank 0 이면 1위부터
     */
    public List<Ranked> page(long version, int afterRank, int limit) {
        Set<TypedTuple<String>> tuples = redis.opsForZSet().rangeByScoreWithScores(
                versionKey(version), afterRank + 1.0, Double.POSITIVE_INFINITY, 0, limit);
        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }

        List<Object> members = tuples.stream().map(TypedTuple::getValue).collect(
                Collectors.toCollection(ArrayList::new));
        List<Object> rawDeltas =
                redis.opsForHash().multiGet(versionKey(version) + DELTA_SUFFIX, members);

        List<Ranked> page = new ArrayList<>(tuples.size());
        int i = 0;
        for (TypedTuple<String> tuple : tuples) {
            Object rawDelta = rawDeltas.get(i++);
            page.add(new Ranked(
                    tuple.getScore() == null ? 0 : tuple.getScore().intValue(),
                    Long.valueOf(tuple.getValue()),
                    rawDelta == null ? null : Integer.valueOf(rawDelta.toString())));
        }
        return page;
    }

    /**
     * 다음 버전 — <b>반드시 직전보다 크다</b>.
     *
     * <p>{@code System.currentTimeMillis()} 만 쓰면 <b>같은 밀리초에 두 번 불렸을 때 버전이
     * 겹친다</b>. 운영 주기(20분)에서는 생기지 않지만 테스트·수동 실행에서는 흔하고, 겹치면
     * 방금 만든 스냅샷을 자기 자신의 대비 대상으로 삼거나 이력에 같은 값이 두 번 들어가
     * {@code rankDelta} 가 조용히 틀린다. 버전은 커서에도 박히는 값이라 단조 증가가
     * 이 구조의 전제다.
     */
    private long nextVersion() {
        long now = System.currentTimeMillis();
        Long last = parseVersion(redis.opsForList().index(VERSIONS, 0));
        return last != null && last >= now ? last + 1 : now;
    }

    /**
     * {@code rankDelta} 의 대비가 되는 버전의 {@code videoSeq → rank}.
     *
     * <p>이력의 {@code rankDeltaBuckets - 1} 번째다 — 아직 이번 버전을 밀어 넣기 전이라
     * 0번이 직전 틱이다. 이력이 그만큼 쌓이지 않았거나 그 버전이 이미 만료됐으면 <b>빈
     * map</b> 이고, 호출부가 그것을 "전원 0" 으로 다룬다.
     *
     * <p>대비를 1틱이 아니라 여러 틱으로 두는 것은 20분마다 변동이 새로 뜨면 표시가
     * 깜빡이기 때문이다(§5.1 이 {@code prev-interval} 을 따로 둔 것과 같은 사정).
     */
    private Map<Long, Integer> baseRanks() {
        String raw = redis.opsForList().index(VERSIONS, properties.ranking().rankDeltaBuckets() - 1L);
        Long version = parseVersion(raw);
        if (version == null) {
            return Map.of();
        }

        Set<TypedTuple<String>> tuples =
                redis.opsForZSet().rangeWithScores(versionKey(version), 0, -1);
        if (tuples == null || tuples.isEmpty()) {
            return Map.of();
        }

        Map<Long, Integer> ranks = new LinkedHashMap<>();
        for (TypedTuple<String> tuple : tuples) {
            if (tuple.getValue() != null && tuple.getScore() != null) {
                ranks.put(Long.valueOf(tuple.getValue()), tuple.getScore().intValue());
            }
        }
        return ranks;
    }

    /** 우리만 쓰는 키라 정상적으로는 깨지지 않는다. 깨졌으면 500 대신 "없음" 으로 본다. */
    private Long parseVersion(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Long.valueOf(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String versionKey(long version) {
        return VERSION_PREFIX + version;
    }
}
