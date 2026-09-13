package com.ssafy.motif.domain.core.song;

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
 * 곡 (DB SPEC v0.15 §11, changeSet 0003-04).
 *
 * <p><b>부분 매핑이다.</b> 챌린지 등록·조회에 필요한 표시 필드만 매핑했다 —
 * {@code reg_usr_seq}·{@code reg_dttm} 은 곡 CRUD 티켓(§3.6)이 함께 추가한다.
 * 매핑하지 않은 컬럼은 {@code ddl-auto: validate} 대상이 아니라 기동에 영향이 없다.
 *
 * <p>{@code is_deleted} 컬럼이 없다 — 곡은 소프트 삭제하지 않으므로 존재 확인이 곧
 * 유효성이다(§3.7.2 검증 1항).
 */
@Entity
@Table(name = "songs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Song {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "song_seq")
    private Integer songSeq;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "singer_name", nullable = false)
    private String singerName;
}
