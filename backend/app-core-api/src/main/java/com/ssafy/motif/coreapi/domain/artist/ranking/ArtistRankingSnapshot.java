package com.ssafy.motif.coreapi.domain.artist.ranking;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 아티스트 랭킹 스냅샷 (API 명세 §5.1) — Redis ZSET 을 만지는 <b>유일한 지점</b>이다.
 *
 * <p>키 이름과 member 표현을 여기만 안다. 조회 서비스와 배치가 각자 키 문자열을 들고
 * 있으면 이름을 바꿀 때 한쪽만 고쳐져 <b>예외도 로그도 없이 빈 랭킹</b>이 된다.
 *
 * <p><b>정렬을 ZSET 에 맡기지 않는다.</b> 동점 순서를 member 사전순에 기대고 싶지만,
 * {@code ZREVRANGE} 는 점수가 같을 때 member 를 <b>역사전순</b>으로 준다 — 0 패딩을
 * 해도 {@code artistSeq} DESC 가 나와 의도와 반대다. 그래서 점수와 함께 전량을 꺼내
 * {@code score DESC → artistSeq ASC} 로 여기서 확정한다. 아티스트가 수백 규모라
 * 전량 읽기가 싸고, §5.1 은 어차피 필터 때문에 전량이 필요하다.
 *
 * <p><b>키에 TTL 을 걸지 않는다.</b> 배치가 매번 덮는다. TTL 을 걸면 배치가 멈췄을 때
 * 랭킹이 통째로 사라지는데, 낡은 랭킹이 없는 랭킹보다 낫다. §1.6 의 "TTL 5분"은
 * 클라이언트 캐시 규약이지 Redis 키 만료가 아니다.
 */
@Component
@RequiredArgsConstructor
public class ArtistRankingSnapshot {

    static final String CURRENT = "rank:artist:current";
    static final String PREV = "rank:artist:prev";

    /** 적재 중인 ZSET. 완성된 뒤 {@code RENAME} 으로 한 번에 갈아끼운다. */
    private static final String BUILDING_SUFFIX = ":building";

    private final StringRedisTemplate redis;

    /** 현재 순위 — {@code score DESC → artistSeq ASC} 로 확정된 {@code artistSeq} 목록. */
    public List<Integer> current() {
        return ranked(CURRENT);
    }

    /**
     * 직전 순위 — {@code artistSeq} → 1-base 순위.
     *
     * <p>{@code rankDelta} 는 이 map 에 <b>없는 것</b>과 <b>있는데 같은 것</b>을 구분해야
     * 한다(신규 진입 {@code null} vs 변동 없음 {@code 0}). 그래서 목록이 아니라 map 이다.
     */
    public Map<Integer, Integer> previousRanks() {
        List<Integer> ranking = ranked(PREV);
        Map<Integer, Integer> ranks = new LinkedHashMap<>();
        for (int i = 0; i < ranking.size(); i++) {
            ranks.put(ranking.get(i), i + 1);
        }
        return ranks;
    }

    public boolean hasPrevious() {
        return Boolean.TRUE.equals(redis.hasKey(PREV));
    }

    public void replaceCurrent(Map<Integer, Double> scores) {
        replace(CURRENT, scores);
    }

    public void replacePrevious(Map<Integer, Double> scores) {
        replace(PREV, scores);
    }

    /**
     * 임시 키에 다 쓴 뒤 {@code RENAME} 으로 갈아끼운다 — 읽는 쪽이 반쯤 찬 ZSET 을
     * 보지 않게 한다.
     *
     * <p><b>비어 있으면 {@code DELETE} 다.</b> Redis 에 빈 키는 존재하지 않아 임시 키가
     * 아예 만들어지지 않고, 그 상태로 {@code RENAME} 하면 "없는 키" 예외가 난다. 아티스트가
     * 0명인 경우(초기 배포·전량 삭제)가 실제로 여기에 걸린다.
     */
    private void replace(String key, Map<Integer, Double> scores) {
        String building = key + BUILDING_SUFFIX;
        redis.delete(building);

        if (scores.isEmpty()) {
            redis.delete(key);
            return;
        }

        Set<TypedTuple<String>> tuples = scores.entrySet().stream()
                .map(entry -> TypedTuple.of(String.valueOf(entry.getKey()), entry.getValue()))
                .collect(Collectors.toSet());
        redis.opsForZSet().add(building, tuples);
        redis.rename(building, key);
    }

    /** 점수째로 꺼내 {@code score DESC → artistSeq ASC} 로 정렬한다 (클래스 javadoc). */
    private List<Integer> ranked(String key) {
        Set<TypedTuple<String>> tuples = redis.opsForZSet().rangeWithScores(key, 0, -1);
        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }
        return tuples.stream()
                .map(Ranked::of)
                .sorted(Comparator.comparingDouble(Ranked::score).reversed()
                        .thenComparing(Ranked::artistSeq))
                .map(Ranked::artistSeq)
                .toList();
    }

    private record Ranked(Integer artistSeq, double score) {

        /** 점수가 {@code null} 로 오는 경우는 없지만, 왔다면 0점과 같게 본다. */
        static Ranked of(TypedTuple<String> tuple) {
            return new Ranked(Integer.valueOf(tuple.getValue()),
                    tuple.getScore() == null ? 0.0 : tuple.getScore());
        }
    }
}
