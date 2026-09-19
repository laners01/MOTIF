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

    /**
     * {@code UPLOADED → QUEUED} (API 명세 §13.2) — <b>원본 업로드가 확인된 뒤</b>.
     *
     * <p>여기서 커밋된 {@code QUEUED} 행을 디스패처가 집어 AI 에 접수한다
     * (작업명세 §2 상태 전이). <b>접수 호출은 이 전이에 들어 있지 않다</b> — 요청
     * 스레드에서 외부 HTTP 를 부르면 커밋 전에 콜백이 와서 404 를 받는다(§7 함정 2).
     *
     * <p>{@code correctionEnabled} 를 함께 확정한다. CHALLENGE 는 사용자가 촬영 후 고른
     * 값이고 MOTIF 는 언제나 {@code false} 다 — 두 값을 따로 쓰면
     * {@code ck_composite_jobs_status_shape} 와 무관한 UPDATE 가 한 번 더 나간다.
     *
     * @throws IllegalStateException {@code UPLOADED} 가 아닐 때. 호출부가 409
     *                               {@code JOB_NOT_REQUESTABLE} 로 먼저 거르므로 여기 걸리면
     *                               거르지 않은 경로가 생긴 것이다
     */
    public void queue(boolean correctionEnabled) {
        if (status != JobStatus.UPLOADED) {
            throw new IllegalStateException("UPLOADED 가 아닌 잡을 큐에 넣을 수 없다: " + status);
        }
        this.correctionEnabled = correctionEnabled;
        this.status = JobStatus.QUEUED;
    }

    /**
     * {@code QUEUED → PROCESSING} — <b>"AI 에 접수됐다"</b> (작업명세 §2).
     *
     * <p>명세의 "워커 처리 중" 과 같은 뜻이고, AI 가 돌든 app-media 가 HLS 를 만들든
     * 이 상태다. 앱은 {@code QUEUED} 와 함께 "진행 중" 하나로 그린다.
     *
     * <p><b>{@code FAILED} 에서도 받는다.</b> 스윕이 {@code STUCK} 으로 닫은 뒤 늦은
     * 콜백이 오는 경우가 있는데, 결과는 실물이라 버리지 않는다(§4.7 마지막 항목).
     * 실패 푸시 뒤에 완료 푸시가 한 번 더 가는 것은 감수한다.
     *
     * <p>{@code fail_code} 를 지운다 — {@code ck_composite_jobs_status_shape} 가
     * {@code PROCESSING} 행에 그 칸이 차 있는 것을 허용하지 않는다.
     */
    public void markSubmitted() {
        if (status != JobStatus.QUEUED && status != JobStatus.PROCESSING
                && status != JobStatus.FAILED) {
            throw new IllegalStateException("접수 상태로 만들 수 없다: " + status);
        }
        this.failCode = null;
        this.status = JobStatus.PROCESSING;
    }

    /**
     * {@code → DONE} + 산출물 키 — <b>한 UPDATE 로 함께 쓴다</b> (작업명세 §7 함정 4).
     *
     * <p>{@code ck_composite_jobs_status_shape} 가 "{@code DONE} 이면 키 둘 다 NOT NULL"
     * 을 강제하므로 <b>상태와 키를 따로 쓰면 반드시 걸린다</b>. app-media 가 그렇게
     * 죽어 있었다 — {@code PROCESSING} 행에 키만 써서 한 번도 성공하지 못했다.
     *
     * <p>이미 {@code DONE} 이면 호출부가 먼저 걸러야 한다 — 중복 {@code packaged} 는
     * 재처리가 아니라 무시다.
     */
    public void complete(String masterKey, String thumbnailKey) {
        if (masterKey == null || thumbnailKey == null) {
            throw new IllegalArgumentException("DONE 에는 산출물 키 둘 다 필요하다");
        }
        if (status == JobStatus.DONE) {
            throw new IllegalStateException("이미 완료된 잡이다");
        }
        this.resultVideoKey = masterKey;
        this.resultThumbnailKey = thumbnailKey;
        this.failCode = null;
        this.status = JobStatus.DONE;
    }

    /**
     * {@code → FAILED} + 사유 — 산출물 키를 <b>지운다</b>.
     *
     * <p>{@link #complete} 와 대칭이다. CHECK 가 {@code FAILED} 행에 결과 키가 남아 있는
     * 것을 허용하지 않으므로, 재시도 뒤 실패한 잡의 옛 키를 비워야 한다.
     *
     * <p>서버가 붙이는 코드는 {@code CHALLENGE_DELETED}·{@code PACKAGING_FAILED}·
     * {@code STUCK} 이고, 나머지는 AI 가 준 값이다(AI 서버 명세 §6).
     */
    public void fail(String failCode) {
        if (failCode == null || failCode.isBlank()) {
            throw new IllegalArgumentException("FAILED 에는 사유 코드가 필요하다");
        }
        this.resultVideoKey = null;
        this.resultThumbnailKey = null;
        this.failCode = failCode;
        this.status = JobStatus.FAILED;
    }

    /**
     * {@code FAILED → QUEUED} + {@code attempt + 1} (API 명세 §13.4).
     *
     * <p><b>같은 행을 재큐한다</b> — 새 잡을 만들지 않으므로 {@code jobUuid} 가 유지되고
     * 클라이언트가 들고 있던 식별자를 계속 쓴다. 결과 프리픽스도 그 uuid 라 재합성이
     * 같은 자리를 덮어쓴다.
     *
     * <p>직전 실패 사유는 여기서 지워진다 — 이력이 필요하면 로그·이벤트로 남긴다(§13.4).
     */
    public void retry() {
        if (status != JobStatus.FAILED) {
            throw new IllegalStateException("실패한 잡만 재시도할 수 있다: " + status);
        }
        this.failCode = null;
        this.attempt = (short) (attempt + 1);
        this.status = JobStatus.QUEUED;
    }

    /**
     * 재접수 — {@code QUEUED} 를 유지한 채 {@code attempt + 1} (작업명세 §4.7).
     *
     * <p>{@link #retry} 와 다르다. 그쪽은 사용자가 <b>실패한</b> 잡을 다시 돌리는
     * §13.4 이고, 이쪽은 <b>접수 자체가 안 된</b> 잡을 디스패처가 다시 밀어 넣는
     * 것이라 상태가 바뀌지 않는다.
     *
     * <p>{@code attempt} 가 무한 루프를 막는 유일한 장치다 — 3회째에 호출부가
     * {@code fail("STUCK")} 으로 닫는다.
     *
     * <p><b>{@code mod_dttm} 이 갱신되는 것이 핵심이다.</b> 정체 판정이
     * {@code COALESCE(mod_dttm, reg_dttm)} 기준이라, 이 UPDATE 가 다음 틱까지의
     * 간격을 다시 벌어 준다 — 안 그러면 매 틱마다 같은 잡을 집는다.
     */
    public void reattempt() {
        if (status != JobStatus.QUEUED) {
            throw new IllegalStateException("QUEUED 잡만 재접수할 수 있다: " + status);
        }
        this.attempt = (short) (attempt + 1);
    }

    /** 워커가 보고한 처리 시간. 상태와 무관한 기록이라 전이와 분리한다. */
    public void recordProcessingMs(Integer processingMs) {
        if (processingMs != null && processingMs >= 0) {
            this.processingMs = processingMs;
        }
    }

    /** 산출물이 준비됐는가. 재생목록·썸네일을 읽기 전에 이걸 먼저 본다. */
    public boolean isDone() {
        return status == JobStatus.DONE;
    }

    public boolean isOwnedBy(Integer candidateUserSeq) {
        return userSeq.equals(candidateUserSeq);
    }
}
