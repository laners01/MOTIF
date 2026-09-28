package com.ssafy.motif.domain.core.purchase;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AssetPurchaseRepository extends JpaRepository<AssetPurchase, Long> {

    /** 주문 하나에 보유권 하나 — {@code uq_asset_purchases_order}. 승인 결과 확인·환불이 이 축으로 집는다. */
    Optional<AssetPurchase> findByOrderSeq(Long orderSeq);
}
