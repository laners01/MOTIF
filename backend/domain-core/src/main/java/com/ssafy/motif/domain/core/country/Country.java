package com.ssafy.motif.domain.core.country;

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

/**
 * 국가 판매 설정 (DB SPEC v0.15 §29, changeSet 0001-01).
 *
 * <p><b>부분 매핑이다.</b> 가격 API(§3.8.6·§3.9.2)가 보는 것은 코드와 활성 여부뿐이다 —
 * 가격 활성 전환이 {@code countries.is_active=TRUE} 를 요구한다(DB §31·§32).
 * 국가 관리 화면이 생기면 나머지 컬럼을 그 티켓에서 더한다.
 *
 * <p>현재 행은 시드 {@code KR} 하나다({@code 0013-seed.sql}).
 */
@Entity
@Table(name = "countries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Country {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "country_seq")
    private Integer countrySeq;

    /** DDL 이 {@code CHAR(2)} 라 JDBC 타입을 맞춘다 — 기본 매핑은 VARCHAR 라 validate 가 막는다. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Column(name = "is_active", nullable = false)
    private boolean active;
}
