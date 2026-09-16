package com.ssafy.motif.domain.core.motif;

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
 * 모티프 결과물 보관함 (DB SPEC v0.15 §36, changeSet 0010-02).
 *
 * <p><b>공개 등급이 없다.</b> 유료 에셋으로 만든 결과물이라 평생 {@code private/} 아래에
 * 남는다(§1.7) — 참여 영상과 달리 {@code visibility} 칸이 없는 것이 그 때문이다.
 * 재생·썸네일 모두 서명이 필요하고, 볼 수 있는 사람은 소유자뿐이다.
 *
 * <p><b>생성자가 없다.</b> 결과물을 만드는 경로는 §13 합성 완료 처리이고 이 티켓
 * 범위가 아니다. 여기서는 마이페이지 대표 모티프(§4.1)를 읽기만 한다.
 *
 * <p>{@code users.rep_motif_result_seq} 가 이 테이블을 가리키지만 <b>FK 가 없다</b> —
 * 순환 참조를 피하려는 논리 참조다(0002 주석). 삭제된 결과물을 가리키는 상태가
 * 생길 수 있으므로 읽는 쪽이 방어해야 한다.
 */
@Entity
@Table(name = "motif_results")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MotifResult extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "result_seq")
    private Long resultSeq;

    @Column(name = "user_seq", nullable = false, updatable = false)
    private Integer userSeq;

    @Column(name = "asset_seq", nullable = false, updatable = false)
    private Integer assetSeq;

    @Column(name = "artist_seq", nullable = false, updatable = false)
    private Integer artistSeq;

    @Column(name = "job_seq", nullable = false, updatable = false)
    private Long jobSeq;

    /** 마스터 재생목록 키 — {@code private/motif/{jobUuid}/master.m3u8} (ADR-0010 D4). */
    @Column(name = "video_key", nullable = false, length = 255)
    private String videoKey;

    @Column(name = "thumbnail_key", nullable = false, length = 255)
    private String thumbnailKey;

    @Column(name = "duration_ms", nullable = false)
    private Integer durationMs;

    /** 보관함 문구. 수정은 §11.8 이 한다. */
    @Column(name = "description")
    private String description;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;
}
