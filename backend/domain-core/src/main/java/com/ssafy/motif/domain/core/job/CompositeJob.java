package com.ssafy.motif.domain.core.job;

import com.ssafy.motif.common.jpa.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 합성 잡 (DB SPEC v0.15 §37, changeSet 0010-01).
 *
 * <p><b>만드는 경로는 {@link #create} 하나다</b>(§13.1). 그 밖에는 {@code jobUuid} 로
 * <b>타입·소유자·산출물 키</b>를 읽기만 한다 — 보호 등급 재생목록(§1.7)을 내주기 전
 * 인가에 그 셋이 필요하다. 상태 전이({@code QUEUED} → {@code DONE})는 app-media 가
 * 네이티브 UPDATE 로 한다({@code MediaJobProcessor}) — 그쪽은 이 엔티티를 쓰지 않는다.
 * {@code UPLOADED} 이후의 전이 메서드는 그 전이를 쓰는 티켓(§13.2·§13.4)이 더한다.
 *
 * <p><b>{@code jobUuid} 가 외부에 노출되는 식별자다.</b> 재생목록 주소
 * ({@code /api/v1/media/{jobUuid}/master.m3u8})와 산출물 S3 프리픽스가 모두 이 값으로
 * 만들어진다. {@code jobSeq} 를 쓰지 않는 이유는 순차 정수가 공개 네임스페이스에
 * 드러나면 콘텐츠의 존재와 개수가 열거되기 때문이다({@code MediaKeys}).
 *
 * <p><b>산출물 키는 {@link JobStatus#DONE} 일 때만 채워져 있다</b> —
 * {@code ck_composite_jobs_status_shape} 가 DB 에서 강제한다. 읽기 전에 상태를 본다.
 */
@Entity
@Table(name = "composite_jobs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompositeJob extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "job_seq")
    private Long jobSeq;

    @Column(name = "job_uuid", nullable = false, updatable = false)
    private UUID jobUuid;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 10, updatable = false)
    private JobType jobType;

    /** 잡을 만든 사람. 보호 등급 인가의 기준이다. */
    @Column(name = "user_seq", nullable = false, updatable = false)
    private Integer userSeq;

    @Column(name = "challenge_seq")
    private Integer challengeSeq;

    @Column(name = "asset_seq")
    private Integer assetSeq;

    @Column(name = "edited_challenge_seq")
    private Integer editedChallengeSeq;

    @Column(name = "raw_video_key", nullable = false, length = 255)
    private String rawVideoKey;

    @Column(name = "correction_enabled", nullable = false)
    private boolean correctionEnabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private JobStatus status;

    @Column(name = "fail_code", length = 30)
    private String failCode;

    /** {@code {프리픽스}/master.m3u8}. {@link JobStatus#DONE} 이 아니면 {@code null}. */
    @Column(name = "result_video_key", length = 255)
    private String resultVideoKey;

    /** {@code {프리픽스}/thumb.jpg}. {@link JobStatus#DONE} 이 아니면 {@code null}. */
    @Column(name = "result_thumbnail_key", length = 255)
    private String resultThumbnailKey;

    @Column(name = "attempt", nullable = false)
    private Short attempt;

    @Column(name = "processing_ms")
    private Integer processingMs;

    private CompositeJob(UUID jobUuid, JobType jobType, Integer userSeq, Integer challengeSeq,
                         Integer assetSeq, Integer editedChallengeSeq, String rawVideoKey) {
        this.jobUuid = jobUuid;
        this.jobType = jobType;
        this.userSeq = userSeq;
        this.challengeSeq = challengeSeq;
        this.assetSeq = assetSeq;
        this.editedChallengeSeq = editedChallengeSeq;
        this.rawVideoKey = rawVideoKey;
        this.correctionEnabled = false;
        this.status = JobStatus.UPLOADED;
        this.attempt = (short) 1;   // Short 라 리터럴이 박스되지 않는다
    }

    /**
     * 잡 생성 (API 명세 §13.1) — <b>원본 업로드 전</b>이라 {@link JobStatus#UPLOADED} 다.
     *
     * <p>이름과 달리 이 상태는 "업로드가 끝났다" 가 아니라 <b>"업로드 URL 을 내줬다"</b> 다.
     * 실제로 올라왔는지는 §13.2 가 S3 HEAD 로 확인하고 그때 {@code QUEUED} 로 넘어간다 —
     * 여기서 그것을 알 방법이 없다(바이트가 서버를 지나지 않는다).
     *
     * <p><b>{@code correctionEnabled} 는 {@code false} 로 둔다.</b> 보정 여부는 촬영이
     * 끝난 뒤 사용자가 고르는 값이라 §13.2 가 덮어쓰고, MOTIF 는 영영 {@code false} 다
     * (§13.2 — 보정을 쓰지 않는다). DB 기본값은 {@code TRUE} 지만 JPA 가 칼럼을 항상
     * 실어 보내므로 여기 값이 들어간다.
     *
     * <p>타입별로 채우는 칸이 갈리는 것은 호출부가 정한다 —
     * {@code ck_composite_jobs_type_shape} 가 CHALLENGE 에 {@code challengeSeq},
     * MOTIF 에 {@code assetSeq} 를 강제하고 MOTIF 의 {@code editedChallengeSeq} 를 막는다.
     *
     * @param editedChallengeSeq 무교체({@code participantMask} 가 0·생략)면 {@code null}
     */
    public static CompositeJob create(UUID jobUuid, JobType jobType, Integer userSeq,
                                      Integer challengeSeq, Integer assetSeq,
                                      Integer editedChallengeSeq, String rawVideoKey) {
        return new CompositeJob(jobUuid, jobType, userSeq, challengeSeq, assetSeq,
                editedChallengeSeq, rawVideoKey);
    }

    /** 산출물이 준비됐는가. 재생목록·썸네일을 읽기 전에 이걸 먼저 본다. */
    public boolean isDone() {
        return status == JobStatus.DONE;
    }

    public boolean isOwnedBy(Integer candidateUserSeq) {
        return userSeq.equals(candidateUserSeq);
    }
}
