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
 * <p><b>만드는 경로는 {@link #of} 하나다</b> — MOTIF 잡의 가공 완료 처리
 * (S15P21D104-249). 사용자가 직접 만드는 경로는 없다.
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

    private MotifResult(Integer userSeq, Integer assetSeq, Integer artistSeq, Long jobSeq,
                        String videoKey, String thumbnailKey, Integer durationMs) {
        this.userSeq = userSeq;
        this.assetSeq = assetSeq;
        this.artistSeq = artistSeq;
        this.jobSeq = jobSeq;
        this.videoKey = videoKey;
        this.thumbnailKey = thumbnailKey;
        this.durationMs = durationMs;
    }

    /**
     * MOTIF 합성 완료 — 보관함 행을 만든다 (API 명세 §13.3 "MOTIF 잡의 완료 순서").
     *
     * <p><b>순서가 계약이다.</b> 결과 행 생성 → 잡 {@code DONE} → FCM. 행 생성이 실패하면
     * {@code DONE} 으로 만들지 않고 푸시도 보내지 않는다(같은 트랜잭션이라 통째로
     * 롤백된다) — 그래서 {@code status=DONE} 인 MOTIF 응답에는 {@code resultSeq} 가
     * 항상 있다.
     *
     * <p><b>{@code description} 은 {@code null}, 대표도 아니다.</b> 문구는 사용자가
     * 나중에 §11.8 로 넣고, 대표 지정도 그쪽이다 — 합성이 끝났다고 대표가 바뀌면
     * 마이페이지가 사용자 동의 없이 달라진다.
     *
     * @param jobSeq      {@code uq_motif_results_job} 이 잡당 한 행을 보장한다 — 중복
     *                    {@code packaged} 의 최종 방어선이다
     * @param videoKey    가공 산출물 {@code private/motif/{jobUuid}/master.m3u8}
     * @param durationMs  가공 이벤트가 원본에서 잰 값. 없으면 에셋 길이를 쓴다(§4.4)
     */
    public static MotifResult of(Integer userSeq, Integer assetSeq, Integer artistSeq, Long jobSeq,
                                 String videoKey, String thumbnailKey, Integer durationMs) {
        return new MotifResult(userSeq, assetSeq, artistSeq, jobSeq,
                videoKey, thumbnailKey, durationMs);
    }

    /**
     * 문구 수정 (§11.8) — <b>{@code null} 은 "보내지 않았다"</b> 다.
     *
     * <p>빈 문자열과 구분한다. 문구는 nullable 이라 "지운다"(빈 문자열)와 "건드리지
     * 않는다"({@code null})가 다른 요청이다({@code MotifAsset#update} 와 같은 규칙).
     *
     * <p>이 엔티티에서 사용자가 바꿀 수 있는 것은 이 칸 하나뿐이다. 나머지는 합성 잡이
     * 채운 사실이라 {@code updatable = false} 로 막혀 있다.
     */
    public void updateDescription(String description) {
        if (description != null) {
            this.description = description;
        }
    }

    /**
     * 소프트 삭제 (§11.8).
     *
     * <p><b>S3 산출물을 지우지 않는다.</b> 되돌릴 수 있어야 하고, 잡 이력은 §13 의
     * 소유다. 여기서 하는 일은 플래그 하나다.
     *
     * <p>대표 모티프였다면 {@code users.rep_motif_result_seq} 를 함께 비워야 하는데,
     * 그건 <b>이 엔티티가 모르는 일</b>이라 서비스가 같은 트랜잭션에서 처리한다 —
     * FK 가 없는 논리 참조라 DB 도 정리해 주지 않는다.
     */
    /** 썸네일 교체 (어드민 §3.17.2). 영상 키는 건드리지 않는다. */
    public void changeThumbnail(String thumbnailKey) {
        this.thumbnailKey = thumbnailKey;
    }

    public void softDelete() {
        this.deleted = true;
    }
}
