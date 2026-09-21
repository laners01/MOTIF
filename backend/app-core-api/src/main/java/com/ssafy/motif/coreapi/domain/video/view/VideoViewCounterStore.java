package com.ssafy.motif.coreapi.domain.video.view;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.util.Map;

/**
 * 누적 조회수 반영 (ADR-0015) — {@code participation_videos.view_count} 를 올리는
 * <b>유일한 지점</b>이다.
 *
 * <p><b>영상별 {@code UPDATE} 를 N번 돌지 않는다.</b> 배열 두 개를 넘겨
 * {@code unnest} 로 펼친 <b>문장 하나</b>로 끝낸다 — 한 구간에 수천 건이 쌓여도 왕복이
 * 한 번이고 서버에서도 한 번 계획된다.
 *
 * <pre>
 * update participation_videos v
 *    set view_count = v.view_count + d.delta
 *   from (select unnest(?::bigint[]) seq, unnest(?::bigint[]) delta) d
 *  where v.video_seq = d.seq
 * </pre>
 *
 * <p><b>JPA 가 아니라 {@link JdbcTemplate} 다.</b> 배열 파라미터를
 * {@code ParticipationVideoRepository} 의 {@code @Query} 로 넘기려면 리스트 확장을
 * 우회하는 문자열 조작이 필요해진다 — {@code setArray} 로 넘기면 타입이 드라이버에서
 * 확정된다({@code DeviceTokenService}·{@code MediaJobProcessor} 와 같은 선택).
 *
 * <p><b>영속성 컨텍스트를 거치지 않는다.</b> 이 경로에서 엔티티를 읽지 않으므로 어긋날
 * 대상이 없고, 랭킹이 쓰는 {@code view_count} 는 이 문장이 커밋된 뒤 새 쿼리로 읽는다.
 *
 * <p><b>{@code is_deleted} 를 조건에 걸지 않는다.</b> {@code view_count} 는 누적 사실의
 * 기록이고 노출 여부는 조회 쿼리가 따로 거른다 — 신고 기각 등으로 되살아났을 때 숫자가
 * 맞아야 한다. 없는 {@code video_seq} 는 매칭되는 행이 없어 <b>조용히 버려진다</b>
 * (§8.5 가 적재 시점에 존재 검증을 하지 않는 근거).
 *
 * <p>트랜잭션을 걸지 않는다 — 문장 하나라 그 자체로 원자적이고, 걸면 Redis 작업 동안
 * 커넥션을 쥐고 있게 된다.
 */
@Component
@RequiredArgsConstructor
public class VideoViewCounterStore {

    private static final String ADD_VIEW_COUNTS = """
            update motif_core.participation_videos v
               set view_count = v.view_count + d.delta
              from (select unnest(?::bigint[]) as seq,
                           unnest(?::bigint[]) as delta) d
             where v.video_seq = d.seq
            """;

    private final JdbcTemplate jdbc;

    /**
     * 구간 합계를 누적 조회수에 더한다.
     *
     * @param deltas {@code videoSeq → 증가분}
     * @return 실제로 갱신된 행 수 — 입력보다 적으면 그만큼이 없는 영상이었다
     */
    public int add(Map<Long, Long> deltas) {
        if (deltas.isEmpty()) {
            return 0;
        }

        Long[] seqs = new Long[deltas.size()];
        Long[] counts = new Long[deltas.size()];
        int i = 0;
        for (Map.Entry<Long, Long> entry : deltas.entrySet()) {
            seqs[i] = entry.getKey();
            counts[i] = entry.getValue();
            i++;
        }

        return jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(ADD_VIEW_COUNTS);
            statement.setArray(1, connection.createArrayOf("bigint", seqs));
            statement.setArray(2, connection.createArrayOf("bigint", counts));
            return statement;
        });
    }
}
