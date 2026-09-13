package com.ssafy.motif.domain.core.artist;

import org.springframework.data.jpa.repository.JpaRepository;

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

    /**
     * 온보딩 완료 판정 (명세 §3.3) — 구독이 1건이라도 있으면 완료로 본다.
     *
     * <p>별도의 완료 플래그 컬럼을 두지 않기로 한 결정(원본 §16 #16)에 따른 파생 판정이라,
     * 구독을 전부 해제한 사용자는 다시 온보딩할 수 있다. 플래그가 도입되면 바뀐다.
     */
    boolean existsByIdUserSeq(Integer userSeq);
}
