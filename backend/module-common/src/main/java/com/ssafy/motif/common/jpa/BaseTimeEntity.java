package com.ssafy.motif.common.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;

import java.time.Instant;

/**
 * 생성/수정 시각 공통 매핑 (팀 표준 — backend/README.md).
 *
 * <ul>
 *   <li>{@code regDttm}: INSERT 시 {@code @PrePersist}로 채운다.</li>
 *   <li>{@code modDttm}: UPDATE 시 {@code @PreUpdate}로 채운다. <b>INSERT 시점에는
 *       NULL</b> — "수정된 적 없음"을 NULL로 구분하는 의도된 규칙이다.</li>
 *   <li>타입은 {@link Instant}, DB 컬럼은 TIMESTAMPTZ (DDL은 Liquibase 소유,
 *       {@code ddl-auto: validate}).</li>
 * </ul>
 */
@Getter
@MappedSuperclass
public abstract class BaseTimeEntity {

    @Column(name = "reg_dttm", nullable = false, updatable = false)
    private Instant regDttm;

    @Column(name = "mod_dttm")
    private Instant modDttm;

    @PrePersist
    protected void onPrePersist() {
        this.regDttm = Instant.now();
    }

    @PreUpdate
    protected void onPreUpdate() {
        this.modDttm = Instant.now();
    }
}
