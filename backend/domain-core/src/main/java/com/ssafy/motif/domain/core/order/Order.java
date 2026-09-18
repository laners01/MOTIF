package com.ssafy.motif.domain.core.order;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * 주문 (DB SPEC v0.15 §33, changeSet 0009-04) — 에셋·PASS 결제 시도의 이력 원천.
 *
 * <p><b>가격을 참조하지 않고 복사한다.</b> {@code motif_asset_prices}·{@code motif_pass_prices}
 * 는 "지금 파는 값"이라 언제든 바뀌는데, 이미 만든 주문의 금액이 따라 바뀌면 결제 위젯이
 * 받은 금액과 승인 시점 금액이 어긋난다. 그래서 가격 행 FK 대신
 * {@code amount}·{@code currency}·{@code country_code}(+{@code pass_duration_months}) 를
 * 스냅샷으로 들고, 승인(§12.2)은 이 스냅샷과 토스 콜백 금액을 대조한다.
 *
 * <p><b>{@code orderUuid} 가 외부에 노출되는 식별자다.</b> 토스에 넘기는 {@code orderId}
 * 로 그대로 쓰이는 외부 잔존 식별자라 숫자 PK 를 내보내지 않는다(API 명세 §1.1 —
 * {@code CompositeJob.jobUuid} 와 같은 규칙). 생성 시 서버가 랜덤으로 뽑는다.
 *
 * <p><b>상품 형태는 팩토리가 지킨다.</b> {@code ck_orders_product_shape} 가 ASSET 이면
 * {@code asset_seq} 만, PASS 면 {@code artist_seq}·{@code pass_duration_months} 만 있기를
 * 요구한다. 칸을 직접 채우는 생성자를 두지 않고 {@link #forAsset}·{@link #forPass} 로만
 * 만들어 잘못된 조합이 DB 까지 가지 않게 한다.
 *
 * <p><b>상태 전이 메서드가 아직 없다.</b> 이 엔티티는 주문 생성(§12.1)이 만들고, 승인
 * ({@code PENDING → PAID})·취소({@code PENDING → CANCELED})·만료 배치는 각자의 티켓
 * (§12.2·§12.3)이 필요한 전이를 붙인다. 승인 전이는 "{@code status='PENDING' AND
 * expires_dttm > now()}" 조건부 UPDATE 로 중복 승인을 막아야 하므로(DB §33) 엔티티
 * setter 가 아니라 리포지토리 벌크 UPDATE 가 될 가능성이 크다.
 *
 * <p>{@code country_code}·{@code currency} 는 DDL 이 {@code CHAR} 라 JDBC 타입을 맞춘다 —
 * 기본 매핑은 VARCHAR 라 {@code ddl-auto: validate} 가 막는다({@code MotifAssetPrice} 와 같다).
 */
@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_seq")
    private Long orderSeq;

    @Column(name = "order_uuid", nullable = false, updatable = false)
    private UUID orderUuid;

    @Column(name = "user_seq", nullable = false, updatable = false)
    private Integer userSeq;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 10, updatable = false)
    private ProductType productType;

    /** ASSET 주문의 대상. PASS 면 {@code null}. */
    @Column(name = "asset_seq", updatable = false)
    private Integer assetSeq;

    /** PASS 주문의 대상. ASSET 이면 {@code null}. */
    @Column(name = "artist_seq", updatable = false)
    private Integer artistSeq;

    /** 주문 시 국가 코드 스냅샷 (ISO 3166-1 alpha-2). */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "country_code", nullable = false, length = 2, updatable = false)
    private String countryCode;

    /** PASS 기간 스냅샷(개월). ASSET 이면 {@code null}. */
    @Column(name = "pass_duration_months", updatable = false)
    private Short passDurationMonths;

    /** 금액 스냅샷 — {@code NUMERIC(10,2)}. 부동소수를 쓰지 않는다. */
    @Column(name = "amount", nullable = false, updatable = false)
    private BigDecimal amount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", nullable = false, length = 3, updatable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private OrderStatus status;

    /** 결제 제공자 코드 — 승인(§12.2)이 {@code TOSS} 를 기록한다. 생성 시점에는 {@code null}. */
    @Column(name = "payment_provider", length = 20)
    private String paymentProvider;

    @Column(name = "payment_key", length = 200)
    private String paymentKey;

    @Column(name = "approved_dttm")
    private Instant approvedDttm;

    @Column(name = "fail_reason", length = 200)
    private String failReason;

    /**
     * 결제 가능 만료 시각 — 생성 + 30분(§12.1). 지나면 승인이 거절되고 배치가
     * {@code EXPIRED} 로 바꾼다.
     */
    @Column(name = "expires_dttm", nullable = false, updatable = false)
    private Instant expiresDttm;

    /**
     * 에셋 주문 (§12.1 ASSET). 존재·판매 가능·보유 판정은 서비스가 끝내고 부른다.
     *
     * @param countryCode 가격을 고른 국가 — 그대로 스냅샷된다
     * @param amount      그 국가의 활성 가격 행에서 복사한 금액
     * @param currency    같은 행의 통화
     */
    public static Order forAsset(Integer userSeq, Integer assetSeq, String countryCode,
                                 BigDecimal amount, String currency, Instant expiresDttm) {
        Order order = pending(userSeq, ProductType.ASSET, countryCode, amount, currency, expiresDttm);
        order.assetSeq = assetSeq;
        return order;
    }

    /**
     * PASS 주문 (§12.1 PASS).
     *
     * @param passDurationMonths 구매하는 개월 수 — 가격 행의 {@code duration_months} 그대로
     */
    public static Order forPass(Integer userSeq, Integer artistSeq, short passDurationMonths,
                                String countryCode, BigDecimal amount, String currency,
                                Instant expiresDttm) {
        Order order = pending(userSeq, ProductType.PASS, countryCode, amount, currency, expiresDttm);
        order.artistSeq = artistSeq;
        order.passDurationMonths = passDurationMonths;
        return order;
    }

    private static Order pending(Integer userSeq, ProductType productType, String countryCode,
                                 BigDecimal amount, String currency, Instant expiresDttm) {
        Order order = new Order();
        order.orderUuid = UUID.randomUUID();
        order.userSeq = userSeq;
        order.productType = productType;
        order.countryCode = countryCode;
        order.amount = amount;
        order.currency = currency;
        order.status = OrderStatus.PENDING;
        order.expiresDttm = expiresDttm;
        return order;
    }
}
