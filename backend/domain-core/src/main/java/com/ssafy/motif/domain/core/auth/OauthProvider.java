package com.ssafy.motif.domain.core.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * OAuth 제공자 마스터 (DB SPEC v0.15 §3, changeSet 0001-02). 현재 행은 {@code GOOGLE} 하나
 * (시드 0013-01).
 *
 * <p><b>BaseTimeEntity를 상속하지 않는다</b> — 이 테이블에는 {@code mod_dttm} 컬럼이
 * 없어(명세상 갱신되지 않는 마스터) 상속하면 {@code ddl-auto: validate}가 실패한다.
 * 팀 표준("모든 엔티티는 BaseTimeEntity 상속")의 의도된 예외다.
 */
@Entity
@Table(name = "oauth_providers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OauthProvider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "provider_seq")
    private Integer providerSeq;

    @Column(name = "provider_code", nullable = false, length = 20)
    private String providerCode;

    @Column(name = "reg_dttm", nullable = false, updatable = false)
    private Instant regDttm;

    @PrePersist
    protected void onPrePersist() {
        this.regDttm = Instant.now();
    }
}
