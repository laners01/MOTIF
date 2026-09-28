package com.ssafy.motif.domain.core.purchase;

import com.ssafy.motif.common.jpa.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 개별 에셋 보유권 (DB SPEC v0.15 §34, changeSet 0009-05).
 *
 * <p><b>만드는 곳은 결제 승인(API 명세 §12.2) 하나다.</b> ASSET 주문의 {@code PAID} 전이와
 * 이 행의 INSERT 는 같은 트랜잭션이다(§34). {@code uq_asset_purchases_active} 가 (회원, 에셋)의
 * {@code ACTIVE} 를 하나로 막으므로, 승인 직전 재검증이 이미 보유한 에셋의 주문을 걸러내지
 * 못하면 여기서 유니크 위반이 난다.
 *
 * <p>{@code REFUNDED} 전이는 어드민 환불의 몫이라 아직 메서드가 없다 — 환불 티켓이 붙인다.
 * 환불 뒤 재구매는 이 행을 되살리지 않고 새 행을 만든다(§34).
 *
 * <p>보유 여부·집계를 묻는 조회({@code MotifAssetRepository.existsActivePurchase}·
 * {@code countPurchasedByArtists} …)는 이 엔티티가 생긴 뒤에도 그쪽에 남아 있다 — 옮기면
 * §4.7·§11 코드를 함께 건드려야 해서 별도 정리 이슈로 뺐다.
 */
@Entity
@Table(name = "asset_purchases")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AssetPurchase extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "purchase_seq")
    private Long purchaseSeq;

    @Column(name = "user_seq", nullable = false, updatable = false)
    private Integer userSeq;

    @Column(name = "asset_seq", nullable = false, updatable = false)
    private Integer assetSeq;

    /** 이 보유권을 만든 PAID 주문 — {@code uq_asset_purchases_order} 로 주문당 하나다. */
    @Column(name = "order_seq", nullable = false, updatable = false)
    private Long orderSeq;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private AssetPurchaseStatus status;

    /** 결제 승인이 부른다 (§12.2 7단계). 주문이 {@code PAID} 로 바뀐 같은 트랜잭션이어야 한다. */
    public static AssetPurchase grant(Integer userSeq, Integer assetSeq, Long orderSeq) {
        AssetPurchase purchase = new AssetPurchase();
        purchase.userSeq = userSeq;
        purchase.assetSeq = assetSeq;
        purchase.orderSeq = orderSeq;
        purchase.status = AssetPurchaseStatus.ACTIVE;
        return purchase;
    }
}
