package com.ssafy.motif.domain.core.artist;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, SubscriptionId> {

    /**
     * 온보딩 완료 판정 (명세 §3.3) — 구독이 1건이라도 있으면 완료로 본다.
     *
     * <p>별도의 완료 플래그 컬럼을 두지 않기로 한 결정(원본 §16 #16)에 따른 파생 판정이라,
     * 구독을 전부 해제한 사용자는 다시 온보딩할 수 있다. 플래그가 도입되면 바뀐다.
     */
    boolean existsByIdUserSeq(Integer userSeq);
}
