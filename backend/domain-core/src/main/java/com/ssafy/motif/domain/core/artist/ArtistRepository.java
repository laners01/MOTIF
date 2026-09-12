package com.ssafy.motif.domain.core.artist;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;

public interface ArtistRepository extends JpaRepository<Artist, Integer> {

    /**
     * 요청된 아티스트가 <b>전부</b> 존재하고 비삭제인지 확인하기 위한 개수 조회.
     *
     * <p>개수만 세는 이유: 어느 것이 없는지 알아도 응답에 담지 않는다(명세 §3.3 은
     * 코드만 반환). 행을 끌어오지 않아 존재 검증으로 충분하다. 호출자는 요청한
     * <b>중복 제거된</b> 개수와 비교한다.
     */
    long countByArtistSeqInAndDeletedFalse(Collection<Integer> artistSeqs);

    /**
     * 구독자 수 증가 — <b>원자적 UPDATE</b> (명세 §3.3, §5.6).
     *
     * <p>엔티티를 읽어 {@code count + 1} 을 써넣으면 동시 구독에서 증가분이 유실된다
     * (lost update). DB 가 현재 값을 기준으로 더하게 맡긴다.
     *
     * <p><b>두 플래그가 같이 있어야 한다.</b>
     * <ul>
     *   <li>{@code clearAutomatically} — 벌크 UPDATE 는 영속성 컨텍스트를 우회하므로,
     *       같은 트랜잭션에서 이미 로드된 {@code Artist} 는 옛 카운터를 들고 있게 된다.
     *       실행 후 컨텍스트를 비워 다음 조회가 DB 값을 읽게 한다.</li>
     *   <li>{@code flushAutomatically} — <b>기본값이 false 다.</b> 이게 없으면 같은
     *       트랜잭션에서 아직 flush 되지 않은 INSERT/UPDATE 가 위 clear 로 <b>통째로
     *       버려진다.</b> §3.3 온보딩이 정확히 이 경우다 — {@code subscriptions} 를
     *       저장한 직후 이 메서드를 부르므로, flush 를 먼저 하지 않으면 구독이
     *       DB 에 남지 않는다(통합 테스트로 확인된 실제 버그).</li>
     * </ul>
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Artist a set a.subscriberCount = a.subscriberCount + 1 where a.artistSeq in :artistSeqs")
    int increaseSubscriberCount(@Param("artistSeqs") Collection<Integer> artistSeqs);
}
