package com.ssafy.motif.domain.core.artist;

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

/**
 * 소속사 (DB SPEC v0.15 §7, changeSet 0001-03).
 *
 * <p>활성 회사명만 UNIQUE 다({@code uq_companies_name_active} — 부분 UNIQUE). 이름 중복
 * 검사는 소속사 CRUD(§3.3) 의 몫이고, 아티스트 쪽은 <b>존재·비삭제만</b> 본다.
 *
 * <p><b>삭제는 소프트 삭제다</b>(§3.3.4). 부분 UNIQUE 가 {@code is_deleted = FALSE} 행만
 * 보므로, 삭제된 회사명은 같은 이름으로 다시 등록할 수 있다 — 의도된 동작이다.
 */
@Entity
@Table(name = "companies")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Company extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "company_seq")
    private Integer companySeq;

    @Column(name = "company_name", nullable = false, length = 50)
    private String companyName;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    @Column(name = "reg_usr_seq", nullable = false, updatable = false)
    private Integer regUsrSeq;

    @Column(name = "mod_usr_seq")
    private Integer modUsrSeq;

    /** 어드민 등록 (§3.3.2). 이름 중복 검사는 서비스가 먼저 끝내고 부른다. */
    public static Company register(String companyName, Integer regUsrSeq) {
        Company company = new Company();
        company.companyName = companyName;
        company.regUsrSeq = regUsrSeq;
        return company;
    }

    /** 회사명 수정 (§3.3.3). */
    public void rename(String companyName, Integer modUsrSeq) {
        this.companyName = companyName;
        this.modUsrSeq = modUsrSeq;
    }

    /** 소프트 삭제 (§3.3.4). 참조 검사는 서비스가 먼저 끝내고 부른다. */
    public void softDelete(Integer modUsrSeq) {
        this.deleted = true;
        this.modUsrSeq = modUsrSeq;
    }
}
