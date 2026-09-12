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
     * <p>{@code @Modifying(clearAutomatically = true)} — 벌크 UPDATE 는 영속성 컨텍스트를
     * 우회하므로, 같은 트랜잭션에서 이미 로드된 {@code Artist} 가 있으면 옛 카운터를
     * 들고 있게 된다. 실행 후 컨텍스트를 비워 다음 조회가 DB 값을 읽게 한다.
     */
    @Modifying(clearAutomatically = true)
    @Query("update Artist a set a.subscriberCount = a.subscriberCount + 1 where a.artistSeq in :artistSeqs")
    int increaseSubscriberCount(@Param("artistSeqs") Collection<Integer> artistSeqs);
}
