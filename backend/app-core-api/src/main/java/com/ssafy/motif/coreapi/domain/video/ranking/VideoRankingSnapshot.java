package com.ssafy.motif.coreapi.domain.video.ranking;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * HOT 참여 영상 랭킹 스냅샷 (API 명세 §6.1) — Redis 를 만지는 <b>유일한 지점</b>이다.
 *
 * <p>키 이름과 member 표현을 여기만 안다. 조회 서비스와 배치가 각자 키 문자열을 들고
 * 있으면 이름을 바꿀 때 한쪽만 고쳐져 <b>예외도 로그도 없이 빈 랭킹</b>이 된다.
 *
 * <pre>
 * rank:video:current      STRING  현재 버전 값 (포인터)
 * rank:video:{version}    ZSET    score = rank(1-base), member = videoSeq
 * </pre>
 *
 * <p><b>왜 버전이 있는가.</b> §6.1 은 커서 페이지네이션이고 커서에 버전이 박힌다 —
 * 배치가 5분마다 순위를 갈아도 진행 중인 스크롤은 같은 순서를 계속 페이지해야 한다
 * (§1.9 구현 노트). 그래서 §5.1 처럼 키 하나를 덮어쓰는 방식을 쓸 수 없고, 갱신마다
 * 새 키를 만들고 포인터를 옮긴다. 원본 명세는 {@code rank:video:current} 를 스냅샷
 * 자체로 적었지만 여기서는 <b>버전 포인터</b>다.
 *
 * <p><b>정렬을 ZSET 에 맡기지 않는다 — score 가 점수가 아니라 rank 다.</b> 점수를 넣고
 * {@code ZREVRANGE} 로 읽으면 동점일 때 member 를 <b>역사전순</b>으로 주는데(§5.1 이
 * 겪은 함정) 커서 페이징에서는 페이지 경계마다 순서가 흔들린다. 순서는 배치가
 * {@code score DESC → videoSeq ASC} 로 확정해 rank 로 못박고, 읽을 때는 rank 구간만
 * 자른다. §5.1 처럼 전량을 꺼내 앱에서 정렬하는 방법은 커서 페이징이라 쓸 수 없다.
 *
 * <p><b>반쯤 찬 ZSET 이 읽히지 않는 이유.</b> 버전 키는 매번 새 이름이라 포인터가
 * 옮겨지기 전에는 아무도 그 키를 모른다 — §5.1 의 {@code :building} + {@code RENAME}
 * 이 필요 없다. 그래서 적재 → TTL → 포인터 순서를 지킨다.
 *
 * <p><b>버전 키에는 TTL 이 있다({@code retain}).</b> §5.1 은 "낡은 랭킹이 없는 랭킹보다
 * 낫다"며 TTL 을 걸지 않았는데, 여기서는 갱신마다 키가 새로 생기므로 TTL 이 없으면
 * 지난 버전이 무한히 쌓인다. 대신 배치가 {@code interval}(5분)마다 새 키를 만들고
 * {@code retain}(15분)이 그보다 넉넉하므로 정상 운영에서 현재 스냅샷이 만료되는 일은
 * 없다. 배치가 {@code retain} 넘게 멈추면 HOT 이 빈 목록이 되는데, "실시간 인기" 를
 * 15분 넘게 낡은 순서로 계속 보여 주는 것보다 낫다고 본다.
 */
@Component
@RequiredArgsConstructor
public class VideoRankingSnapshot {

    /** 현재 버전 포인터. */
    static final String POINTER = "rank:video:current";

    private static final String VERSION_PREFIX = "rank:video:";

    private final StringRedisTemplate redis;
    private final VideoRankingProperties properties;

    /**
     * 확정된 순서를 새 버전으로 적재하고 포인터를 옮긴다.
     *
     * <p><b>빈 목록은 받지 않는다</b> — {@link #clear()} 를 쓴다. Redis 에 빈 키는
     * 존재하지 않아 ZADD 가 아무것도 만들지 않고, 그 상태로 포인터를 옮기면 "있는
     * 버전을 가리키는데 비어 있다" 와 "버전이 없다" 가 구분되지 않는다. 호출부에서
     * 후보 0건을 눈에 보이게 다루게 하려는 의도다.
     *
     * @param ordered rank 1부터의 {@code videoSeq} 목록 (배치가 확정한 순서)
     * @return 적재한 버전 — 로그·테스트용
     */
    public long replace(List<Long> ordered) {
        if (ordered.isEmpty()) {
            throw new IllegalArgumentException("빈 스냅샷은 clear() 로 지운다");
        }

        long version = System.currentTimeMillis();
        String key = versionKey(version);
        // 같은 밀리초에 두 번 불리면(테스트·수동 실행) 이전 member 가 남는다
        redis.delete(key);

        Set<TypedTuple<String>> tuples = new LinkedHashSet<>();
        for (int i = 0; i < ordered.size(); i++) {
            tuples.add(TypedTuple.of(String.valueOf(ordered.get(i)), (double) (i + 1)));
        }
        redis.opsForZSet().add(key, tuples);
        redis.expire(key, properties.retain());

        // 포인터를 마지막에 옮긴다 — 그 전까지 이 버전은 아무도 모른다
        redis.opsForValue().set(POINTER, String.valueOf(version));
        return version;
    }

    /**
     * 포인터를 지운다 — 후보가 0건일 때.
     *
     * <p>지난 버전 키는 건드리지 않는다. 진행 중인 커서 시퀀스는 TTL 이 남은 동안
     * 계속 동작한다 — 새 진입만 빈 목록이 된다.
     */
    public void clear() {
        redis.delete(POINTER);
    }

    /** 현재 버전. 포인터가 없거나 값이 깨져 있으면 {@code null}(= 스냅샷 없음). */
    public Long currentVersion() {
        String value = redis.opsForValue().get(POINTER);
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            // 우리만 쓰는 키라 정상적으로는 생기지 않는다. 500 을 내는 대신 "없음" 으로
            // 본다 — 다음 배치 틱이 덮는다
            return null;
        }
    }

    /** 스냅샷 한 자리 — 순위와 영상. */
    public record Ranked(int rank, Long videoSeq) {
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
     * @param afterRank 0 이면 1위부터
     */
    public List<Ranked> page(long version, int afterRank, int limit) {
        Set<TypedTuple<String>> tuples = redis.opsForZSet().rangeByScoreWithScores(
                versionKey(version), afterRank + 1.0, Double.POSITIVE_INFINITY, 0, limit);
        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }
        return tuples.stream()
                .map(tuple -> new Ranked(
                        tuple.getScore() == null ? 0 : tuple.getScore().intValue(),
                        Long.valueOf(tuple.getValue())))
                .toList();
    }

    private String versionKey(long version) {
        return VERSION_PREFIX + version;
    }
}
