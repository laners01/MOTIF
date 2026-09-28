package com.ssafy.motif.coreapi.domain.artist.ranking;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 아티스트 랭킹 스냅샷 (API 명세 §5.1 · ADR-0016) — 랭킹 Redis 키를 아는
 * <b>유일한 지점</b>이다.
 *
 * <pre>
 * rank:artist:current           STRING  현재 버전 값 (포인터)
 * rank:artist:{version}         ZSET    score = rank(1-base), member = artistSeq
 * rank:artist:{version}:delta   HASH    field = artistSeq, value = rankDelta
 * rank:artist:versions          LIST    최신 version 이 앞 — rankDelta 대비 대상을 찾는다
 * </pre>
 *
 * <p><b>§6.1 {@code VideoRankingSnapshot} 과 같은 버전 구조다</b> *(2026-09-21 —
 * ADR-0016)*. 예전에는 {@code :current}/{@code :prev} 두 키를 <b>서로 다른 주기</b>로
 * 굴려 {@code rankDelta} 를 얻었는데, 집계가 20분 단일 배치로 합쳐지면서 그 별도 주기를
 * 둘 자리가 없어졌다. 틱을 세는 방식은 재시작에 취약해 쓰지 않는다.
 *
 * <p><b>정렬을 ZSET 에 맡기지 않는다 — score 가 점수가 아니라 rank 다.</b> 점수를 넣고
 * {@code ZREVRANGE} 로 읽으면 동점일 때 member 를 <b>역사전순</b>으로 주는데, 0 패딩을
 * 해도 {@code artistSeq} DESC 가 나와 의도와 반대다. 조회·참여가 모두 0인 아티스트가
 * 많아 <b>동점이 대량으로 발생하므로</b> 실제로 문제가 된다. 순서는 적재 시점에
 * {@code score DESC → artistSeq ASC} 로 확정한다 — 예전처럼 전량을 꺼내 애플리케이션에서
 * 정렬하지 않는다.
 *
 * <p><b>적재 개수 상한이 없다.</b> §6.1 은 상위 {@code keep} 개만 담지만 §5.1 은
 * 필터링과 {@code totalElements} 때문에 전량이 필요하다(offset 페이지) — 자르면 페이지
 * 수가 틀어진다.
 *
 * <p><b>{@code retain} 의 역할이 §6.1 과 다르다.</b> 여기는 커서가 없으므로 TTL 은 오직
 * <b>{@code rankDelta} 대비 버전의 보존</b>을 위한 것이다. 집계 주기 ×
 * {@code rankDeltaBuckets} 보다 짧으면 대비 대상이 언제나 만료돼 있어 <b>순위 변동이
 * 조용히 전원 {@code null}</b> 이 된다 — 예외도 로그도 남지 않는다.
 */
@Component
@RequiredArgsConstructor
public class ArtistRankingSnapshot {

    /** 현재 버전 포인터. */
    static final String POINTER = "rank:artist:current";

    /** 버전 이력 — {@code rankDelta} 의 대비 대상을 여기서 찾는다. */
    static final String VERSIONS = "rank:artist:versions";

    private static final String VERSION_PREFIX = "rank:artist:";
    private static final String DELTA_SUFFIX = ":delta";

    private final StringRedisTemplate redis;
    private final RankingProperties properties;

    /** 읽은 한 벌 — 순위와 변동을 <b>같은 버전에서</b> 가져온다. */
    public record Snapshot(List<Integer> ranking, Map<Integer, Integer> deltas) {

        public boolean isEmpty() {
            return ranking.isEmpty();
        }

        static Snapshot empty() {
            return new Snapshot(List.of(), Map.of());
        }
    }

    /**
     * 점수를 순위로 확정해 새 버전으로 적재하고 포인터를 옮긴다.
     *
     * <p><b>순서는 여기서 확정한다</b>({@code score DESC → artistSeq ASC}). 동점 보조 키가
     * {@code artistSeq} 오름차순인 것은 먼저 등록된 아티스트를 앞에 두려는 것이다.
     *
     * <p>적재 → TTL → 버전 이력 → 포인터 순서를 지킨다. 포인터를 마지막에 옮기므로 그
     * 전까지 이 버전은 아무도 모른다 — 예전의 {@code :building} + {@code RENAME} 이
     * 필요 없는 이유다.
     *
     * <p><b>비어 있으면 포인터를 지운다.</b> Redis 에 빈 키는 존재하지 않아 ZADD 가
     * 아무것도 만들지 않는데, 그 상태로 포인터를 옮기면 "있는 버전을 가리키는데 비어
     * 있다" 와 "버전이 없다" 가 구분되지 않는다. 아티스트가 0명인 경우(초기 배포·전량
     * 삭제)가 실제로 여기에 걸린다.
     *
     * @return 적재한 버전 — 로그·테스트용. 비었으면 {@code null}
     */
    public Long replace(Map<Integer, Double> scores) {
        if (scores.isEmpty()) {
            clear();
            return null;
        }

        List<Integer> ordered = scores.entrySet().stream()
                .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed()
                        .thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey)
                .toList();

        // 이력을 밀기 전에 읽는다 — 밀고 나면 인덱스가 한 칸씩 어긋난다
        Map<Integer, Integer> base = baseRanks();

        long version = nextVersion();
        String key = versionKey(version);
        String deltaKey = key + DELTA_SUFFIX;
        redis.delete(List.of(key, deltaKey));

        Set<TypedTuple<String>> tuples = new LinkedHashSet<>();
        Map<String, String> deltas = new LinkedHashMap<>();
        for (int i = 0; i < ordered.size(); i++) {
            Integer artistSeq = ordered.get(i);
            int rank = i + 1;
            tuples.add(TypedTuple.of(String.valueOf(artistSeq), (double) rank));

            Integer previousRank = base.get(artistSeq);
            if (previousRank != null) {
                // 상승이 양수다 — 5위에서 3위면 +2
                deltas.put(String.valueOf(artistSeq), String.valueOf(previousRank - rank));
            } else if (base.isEmpty()) {
                // 대비할 버전이 아예 없는 첫 틱이다. 전원 0 으로 심어야 null 이 진짜
                // 신규 아티스트 전용으로 남는다 — 배포 직후 전원이 "신규 진입" 으로
                // 보이는 것보다 낫다
                deltas.put(String.valueOf(artistSeq), "0");
            }
            // 대비 버전은 있는데 거기 없던 아티스트 — field 를 넣지 않는다(= null, 신규)
        }

        redis.opsForZSet().add(key, tuples);
        redis.opsForHash().putAll(deltaKey, deltas);
        redis.expire(key, properties.retain());
        redis.expire(deltaKey, properties.retain());

        redis.opsForList().leftPush(VERSIONS, String.valueOf(version));
        redis.opsForList().trim(VERSIONS, 0, properties.rankDeltaBuckets() + 1L);

        redis.opsForValue().set(POINTER, String.valueOf(version));
        return version;
    }

    /**
     * 포인터를 지운다 — 아티스트가 0명일 때.
     *
     * <p>지난 버전 키와 이력은 건드리지 않는다. TTL 이 치운다.
     */
    public void clear() {
        redis.delete(POINTER);
    }

    /**
     * 현재 순위와 변동을 함께 읽는다.
     *
     * <p><b>포인터를 한 번만 읽는다.</b> 순위와 변동을 따로 조회하면 그 사이에 배치가
     * 포인터를 옮겼을 때 <b>서로 다른 버전의 값</b>이 섞인다 — 순위는 새것인데 변동은
     * 옛것이 된다.
     *
     * <p>{@code score = rank} 라 {@code rangeWithScores} 가 이미 rank 순이다.
     */
    public Snapshot read() {
        Long version = currentVersion();
        if (version == null) {
            return Snapshot.empty();
        }

        Set<TypedTuple<String>> tuples =
                redis.opsForZSet().rangeWithScores(versionKey(version), 0, -1);
        if (tuples == null || tuples.isEmpty()) {
            // 버전 키가 만료됐다 — 포인터만 남은 상태다
            return Snapshot.empty();
        }

        List<Integer> ranking = new ArrayList<>(tuples.size());
        List<Object> members = new ArrayList<>(tuples.size());
        tuples.stream()
                .sorted(Comparator.comparingDouble(tuple ->
                        tuple.getScore() == null ? Double.MAX_VALUE : tuple.getScore()))
                .forEach(tuple -> {
                    ranking.add(Integer.valueOf(tuple.getValue()));
                    members.add(tuple.getValue());
                });

        List<Object> rawDeltas =
                redis.opsForHash().multiGet(versionKey(version) + DELTA_SUFFIX, members);
        Map<Integer, Integer> deltas = new LinkedHashMap<>();
        for (int i = 0; i < ranking.size(); i++) {
            Object raw = rawDeltas.get(i);
            if (raw != null) {
                deltas.put(ranking.get(i), Integer.valueOf(raw.toString()));
            }
        }
        return new Snapshot(ranking, deltas);
    }

    /** 현재 버전. 포인터가 없거나 값이 깨져 있으면 {@code null}(= 스냅샷 없음). */
    public Long currentVersion() {
        return parseVersion(redis.opsForValue().get(POINTER));
    }

    /**
     * 다음 버전 — <b>반드시 직전보다 크다</b>.
     *
     * <p>{@code System.currentTimeMillis()} 만 쓰면 같은 밀리초에 두 번 불렸을 때 버전이
     * 겹친다. 운영 주기(20분)에서는 생기지 않지만 테스트·수동 실행에서는 흔하고, 겹치면
     * 방금 만든 스냅샷을 자기 자신의 대비 대상으로 삼아 {@code rankDelta} 가 조용히 틀린다.
     */
    private long nextVersion() {
        long now = System.currentTimeMillis();
        Long last = parseVersion(redis.opsForList().index(VERSIONS, 0));
        return last != null && last >= now ? last + 1 : now;
    }

    /**
     * {@code rankDelta} 의 대비가 되는 버전의 {@code artistSeq → rank}.
     *
     * <p>이력의 {@code rankDeltaBuckets - 1} 번째다 — 아직 이번 버전을 밀어 넣기 전이라
     * 0번이 직전 틱이다. 이력이 그만큼 쌓이지 않았거나 그 버전이 이미 만료됐으면 <b>빈
     * map</b> 이고, 호출부가 그것을 "전원 0" 으로 다룬다.
     */
    private Map<Integer, Integer> baseRanks() {
        Long version = parseVersion(
                redis.opsForList().index(VERSIONS, properties.rankDeltaBuckets() - 1L));
        if (version == null) {
            return Map.of();
        }

        Set<TypedTuple<String>> tuples =
                redis.opsForZSet().rangeWithScores(versionKey(version), 0, -1);
        if (tuples == null || tuples.isEmpty()) {
            return Map.of();
        }

        Map<Integer, Integer> ranks = new LinkedHashMap<>();
        for (TypedTuple<String> tuple : tuples) {
            if (tuple.getValue() != null && tuple.getScore() != null) {
                ranks.put(Integer.valueOf(tuple.getValue()), tuple.getScore().intValue());
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
