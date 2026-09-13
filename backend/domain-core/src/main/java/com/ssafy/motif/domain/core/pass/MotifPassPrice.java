package com.ssafy.motif.domain.core.pass;

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
 * 모티프 PASS 국가·기간별 가격 (DB SPEC v0.15 §32, changeSet 0009-03).
 *
 * <p><b>이 행은 "지금 파는 값"이지 이미 팔린 값이 아니다.</b> 갱신은 이후 주문부터
 * 적용되고, 이미 취득한 ACTIVE PASS 의 금액·만료는 바뀌지 않는다 — 결제 시점 금액은
 * {@code orders} 의 스냅샷이 갖는다(§32).
 *
 * <p>{@code duration_months} 를 1/6/12 로 고정하지 않는다(DB CHECK 는 {@code > 0} 만).
 * 금액은 {@code NUMERIC(10,2)} ↔ {@link BigDecimal} — 부동소수를 쓰지 않는다.
 */
@Entity
@Table(name = "motif_pass_prices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MotifPassPrice extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pass_price_seq")
    private Integer passPriceSeq;

    @Column(name = "artist_seq", nullable = false)
    private Integer artistSeq;

    @Column(name = "country_seq", nullable = false)
    private Integer countrySeq;

    @Column(name = "duration_months", nullable = false)
    private short durationMonths;

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

    public static MotifPassPrice of(Integer artistSeq, Integer countrySeq, short durationMonths,
                                    BigDecimal amount, String currency, boolean active,
                                    Integer regUsrSeq) {
        MotifPassPrice price = new MotifPassPrice();
        price.artistSeq = artistSeq;
        price.countrySeq = countrySeq;
        price.durationMonths = durationMonths;
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
