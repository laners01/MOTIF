package com.ssafy.motif.domain.core.asset;

import com.ssafy.motif.common.jpa.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * 에셋 국가별 현재 판매가 (DB SPEC v0.15 §31, changeSet 0009-02).
 *
 * <p><b>가격 이력 테이블은 없다.</b> 이 행은 항상 "지금 파는 값"이고, 이미 팔린 건의
 * 금액은 {@code orders} 의 스냅샷이 갖는다 — 그래서 금액·통화·활성 변경이 기존 구매
 * 권한에 아무 영향을 주지 않는다(§31).
 *
 * <p>금액은 {@code NUMERIC(10,2)} ↔ {@link BigDecimal} 이다. <b>{@code double}·
 * {@code float} 를 쓰지 않는다</b> — 2진 부동소수는 10진 금액을 정확히 담지 못해 합계가
 * 어긋난다.
 */
@Entity
@Table(name = "motif_asset_prices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MotifAssetPrice extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "asset_price_seq")
    private Integer assetPriceSeq;

    @Column(name = "asset_seq", nullable = false)
    private Integer assetSeq;

    @Column(name = "country_seq", nullable = false)
    private Integer countrySeq;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    /** DDL 이 {@code CHAR(3)} 이라 JDBC 타입을 맞춘다 — 기본 매핑은 VARCHAR 라 validate 가 막는다. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "reg_usr_seq", nullable = false, updatable = false)
    private Integer regUsrSeq;

    @Column(name = "mod_usr_seq")
    private Integer modUsrSeq;

    public static MotifAssetPrice of(Integer assetSeq, Integer countrySeq, BigDecimal amount,
                                     String currency, boolean active, Integer regUsrSeq) {
        MotifAssetPrice price = new MotifAssetPrice();
        price.assetSeq = assetSeq;
        price.countrySeq = countrySeq;
        price.amount = amount;
        price.currency = currency;
        price.active = active;
        price.regUsrSeq = regUsrSeq;
        return price;
    }

    public void update(BigDecimal amount, String currency, boolean active, Integer modUsrSeq) {
        this.amount = amount;
        this.currency = currency;
        this.active = active;
        this.modUsrSeq = modUsrSeq;
    }
}
