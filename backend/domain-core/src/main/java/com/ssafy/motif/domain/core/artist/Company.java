package com.ssafy.motif.domain.core.artist;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 소속사 (DB SPEC v0.15 §7, changeSet 0001-03).
 *
 * <p>활성 회사명만 UNIQUE 다({@code uq_companies_name_active} — 부분 UNIQUE). 이름 중복
 * 검사는 소속사 CRUD(§3.3) 의 몫이고, 아티스트 쪽은 <b>존재·비삭제만</b> 본다.
 *
 * <p><b>부분 매핑이다.</b> 감사 칼럼은 §3.3 티켓에서 더한다.
 */
@Entity
@Table(name = "companies")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "company_seq")
    private Integer companySeq;

    @Column(name = "company_name", nullable = false, length = 50)
    private String companyName;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;
}
