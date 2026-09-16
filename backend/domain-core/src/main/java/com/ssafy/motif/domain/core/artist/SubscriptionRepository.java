package com.ssafy.motif.domain.core.artist;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, SubscriptionId> {

    /**
     * 아티스트 상세의 {@code isSubscribed}·{@code subscribedDays} (명세 §5.3).
     *
     * <p>{@code existsById} 로는 모자라다 — §5.3 은 구독 여부와 함께 "WITH n days" 를
     * 요구하고 그 기준이 {@code reg_dttm} 이라, 존재 확인과 경과일 계산이 <b>같은 한 행</b>
     * 에서 나와야 한다. 두 번 조회하면 그 사이 구독이 해제됐을 때 {@code isSubscribed=true}
     * 인데 날짜가 없는 응답이 나온다.
     */
    Optional<Subscription> findByIdUserSeqAndIdArtistSeq(Integer userSeq, Integer artistSeq);

    /** 챌린지 상세의 {@code isSubscribed} (명세 §7.2) — 여기는 경과일이 필요 없다. */
    boolean existsByIdUserSeqAndIdArtistSeq(Integer userSeq, Integer artistSeq);

    /**
     * 온보딩 완료 판정 (명세 §3.3) — 구독이 1건이라도 있으면 완료로 본다.
     *
     * <p>별도의 완료 플래그 컬럼을 두지 않기로 한 결정(원본 §16 #16)에 따른 파생 판정이라,
     * 구독을 전부 해제한 사용자는 다시 온보딩할 수 있다. 플래그가 도입되면 바뀐다.
     */
    /**
     * 한 페이지 분 {@code isSubscribed} 를 한 번에 (API 명세 §7.1).
     *
     * <p>피드 20건에 아티스트가 몇 명이든 조회는 한 번이다. 항목마다
     * {@link #existsByIdUserSeqAndIdArtistSeq} 를 부르면 그대로 N+1 이다.
     */
    @Query(value = """
            select artist_seq from motif_core.subscriptions
             where user_seq = :userSeq and artist_seq in (:artistSeqs)
            """, nativeQuery = true)
    List<Integer> findSubscribedArtistSeqs(@Param("userSeq") Integer userSeq,
                                           @Param("artistSeqs") Collection<Integer> artistSeqs);

    boolean existsByIdUserSeq(Integer userSeq);

    /**
     * 구독 중인 아티스트 카드 (API 명세 §4.6) — <b>구독 최신순</b>, 비페이지 전량.
     *
     * <p><b>삭제된 아티스트는 뺀다.</b> 명세에 적혀 있지 않지만 §1.8 이 삭제 아티스트를
     * "없는 것으로 답한다"고 정했고(§5.3 은 404), 남겨 두면 <b>눌러도 404 가 나는 카드</b>
     * 가 목록에 생긴다. 구독 행 자체는 지우지 않으므로 여기서 거른다.
     *
     * <p>정렬 기준은 {@code subscriptions.reg_dttm} 이다 — 명세는 §4.6 의 순서를 말하지
     * 않지만 §4.7 이 "구독 최신순"으로 못 박았다. 같은 목록을 두 API 가 다른 순서로
     * 주지 않게 맞춘다.
     *
     * <p>{@code artist_groups} 는 솔로면 {@code group_seq} 가 NULL 이라 left join 이다.
     */
    @Query("""
            select new com.ssafy.motif.domain.core.artist.SubscribedArtistRow(
                       a.artistSeq, u.nickname, u.profileImageKey, g.groupName)
            from Subscription s
              join Artist a on a.artistSeq = s.id.artistSeq
              join User u on u.userSeq = a.userSeq
              left join ArtistGroup g on g.groupSeq = a.groupSeq
            where s.id.userSeq = :userSeq and a.deleted = false
            order by s.regDttm desc, a.artistSeq desc
            """)
    List<SubscribedArtistRow> findSubscribed(@Param("userSeq") Integer userSeq);
}
