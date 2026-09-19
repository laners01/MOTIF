package com.ssafy.motif.coreapi.domain.composite.service;

import com.ssafy.motif.common.error.CommonErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.domain.composite.CompositeErrorCode;
import com.ssafy.motif.coreapi.domain.composite.dto.WorkerDoneRequest;
import com.ssafy.motif.coreapi.domain.composite.dto.WorkerFailedRequest;
import com.ssafy.motif.coreapi.domain.media.MediaJobPublisher;
import com.ssafy.motif.coreapi.domain.media.MediaKeys;
import com.ssafy.motif.event.contract.CompositeFinishedEvent;
import com.ssafy.motif.event.contract.MediaJobRequestedEvent;
import com.ssafy.motif.event.contract.CompositePackagedEvent;
import com.ssafy.motif.domain.core.asset.MotifAsset;
import com.ssafy.motif.domain.core.asset.MotifAssetRepository;
import com.ssafy.motif.domain.core.challenge.ChallengeRepository;
import com.ssafy.motif.domain.core.job.CompositeJob;
import com.ssafy.motif.domain.core.job.CompositeJobRepository;
import com.ssafy.motif.domain.core.job.JobStatus;
import com.ssafy.motif.domain.core.job.JobType;
import com.ssafy.motif.domain.core.motif.MotifResult;
import com.ssafy.motif.domain.core.motif.MotifResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * 잡 상태 전이의 <b>단일 주체</b> (작업명세 §2 원칙 1·2 · S15P21D104-189·249·318).
 *
 * <p>AI 콜백(536)·디스패처(537)·스윕·가공 완료가 전부 여기를 지난다. 전이와 함께
 * {@code composite.finished} 를 내는 곳도 여기 하나다 — 발행을 전이마다 흩어 놓으면
 * 어떤 경로는 푸시가 빠지고 어떤 경로는 두 번 간다.
 *
 * <p><b>{@code @Transactional} 이 필수다</b>(§7 함정 1). 릴레이가
 * {@code AFTER_COMMIT} 이라 트랜잭션이 없으면 <b>예외도 로그도 없이</b> 발행이
 * 통째로 빠진다 — 잡은 {@code DONE} 인데 푸시만 안 가고 흔적이 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompositeJobCompletionService {

    /** 서버가 붙이는 실패 코드 — AI 코드표(AI 서버 명세 §6)와 겹치지 않는다. */
    public static final String FAIL_CHALLENGE_DELETED = "CHALLENGE_DELETED";

    private final CompositeJobRepository compositeJobRepository;
    private final ChallengeRepository challengeRepository;
    private final MotifAssetRepository motifAssetRepository;
    private final MotifResultRepository motifResultRepository;
    private final MediaJobPublisher mediaJobPublisher;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 접수 202 → {@code QUEUED → PROCESSING} 을 <b>새 트랜잭션</b>으로 (작업명세 §4.2).
     *
     * <p><b>{@code REQUIRES_NEW} 인 이유.</b> 부르는 쪽(접수 호출기)이 디스패처·스윕이라
     * 원 트랜잭션이 이미 끝나 있거나 아예 없다 — 그 상태에서 엔티티를 고치면 플러시될
     * 곳이 없어 <b>조용히 사라진다.</b>
     *
     * <p>잡이 없거나 전이가 어긋나면 로그만 남긴다. 접수는 이미 성공했으므로 늦어도
     * 콜백이 {@code QUEUED} 에서 따라잡는다({@link #aiDone}).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void submitted(UUID jobUuid) {
        CompositeJob job = compositeJobRepository.findByJobUuid(jobUuid).orElse(null);
        if (job == null) {
            log.warn("접수한 잡을 찾지 못했다: jobUuid={}", jobUuid);
            return;
        }
        try {
            job.markSubmitted();
        } catch (IllegalStateException e) {
            log.warn("접수 후 전이 실패 — 상태를 둔다: jobUuid={} {}", jobUuid, e.getMessage());
        }
    }

    /**
     * AI 합성 성공 콜백 (작업명세 §4.3 · AI 서버 명세 §3.3) — <b>아직 {@code DONE} 이 아니다</b>.
     *
     * <p>AI 가 끝냈다는 것은 {@code results/…mp4} 가 S3 에 올라왔다는 뜻이고, 앱이 재생할
     * HLS 는 app-media 가 그 뒤에 만든다. 그래서 여기서는 {@code job.media} 를 발행하고
     * 상태는 {@code PROCESSING} 에 둔다 — {@code DONE} 은 {@code composite.packaged} 가
     * 확정한다(189).
     *
     * <p><b>{@code result_video_key} 를 믿지 않는다.</b> 콜백은 인터넷에서 오는 입력이라
     * 그대로 가공 대상으로 넣으면 남의 잡 결과나 엉뚱한 프리픽스를 읽게 된다. 서버가
     * {@code MediaKeys.forWorkerOutput} 으로 계산한 값과 <b>대조만</b> 하고, 다르면 400 이다.
     *
     * <p><b>{@code QUEUED} 에서 와도 받는다</b> — 접수 202 응답이 유실돼 우리가
     * {@code markSubmitted()} 를 못 했는데 AI 는 정상으로 돈 경우다(§2 상태 전이표).
     *
     * <p>중복 {@code /done} 은 {@code job.media} 를 한 번 더 낼 수 있다. app-media 가
     * {@code alreadyDone} 으로 거르고, 안 걸려도 같은 프리픽스에 같은 결과다 — 따로 막지
     * 않는다(§5 멱등 표).
     *
     * @throws BusinessException 404 {@code JOB_NOT_FOUND} · 400 {@code INVALID_PARAMETER}
     */
    @Transactional
    public void aiDone(UUID jobUuid, WorkerDoneRequest request) {
        CompositeJob job = job(jobUuid);
        if (job.isDone()) {
            log.info("/done 무시 — 이미 DONE: jobUuid={}", jobUuid);
            return;
        }
        String expected = MediaKeys.forWorkerOutput(job.getJobType().name(), jobUuid.toString());
        if (!expected.equals(request.resultVideoKey())) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER,
                    "result_video_key 가 계산값과 다르다 — expected=" + expected
                            + " actual=" + request.resultVideoKey());
        }
        if (job.getStatus() == JobStatus.QUEUED) {
            // 접수 202 를 놓쳤다. AI 는 정상으로 돌았으므로 여기서 따라잡는다
            job.markSubmitted();
        }
        job.recordProcessingMs(request.processingMs());
        mediaJobPublisher.publish(
                MediaJobRequestedEvent.SOURCE_COMPOSITE_RESULT,
                job.getJobSeq(),
                expected,
                MediaKeys.forCompositeResult(job.getJobType().name(), jobUuid.toString()),
                null);
        log.info("/done 수신 → job.media 발행: jobUuid={} workerId={} processingMs={}",
                jobUuid, request.workerId(), request.processingMs());
    }

    /**
     * AI 합성 실패 콜백 (작업명세 §4.3 · AI 서버 명세 §3.4).
     *
     * <p><b>{@code failMessage} 를 저장하지 않는다.</b> 사용자에게 보이지 않는 로그용
     * 문장이고({@code fail_code} 로 앱이 문구를 고른다), 잡 행에 칸도 없다.
     *
     * @throws BusinessException 404 {@code JOB_NOT_FOUND}
     */
    @Transactional
    public void aiFailed(UUID jobUuid, WorkerFailedRequest request) {
        CompositeJob job = job(jobUuid);
        job.recordProcessingMs(request.processingMs());
        log.info("/failed 수신: jobUuid={} failCode={} failMessage={} workerId={}",
                jobUuid, request.failCode(), request.failMessage(), request.workerId());
        fail(job, request.failCode());
    }

    /**
     * 콜백이 가리키는 잡 — 없으면 404 (AI 명세 §3.5: AI 는 재시도하지 않는다).
     *
     * <p>소유자를 보지 않는다 — 부르는 쪽이 사용자가 아니라 AI 다. 인가는 토큰이 이미 했다.
     */
    private CompositeJob job(UUID jobUuid) {
        return compositeJobRepository.findByJobUuid(jobUuid)
                .orElseThrow(() -> new BusinessException(
                        CompositeErrorCode.JOB_NOT_FOUND, "jobUuid: " + jobUuid));
    }

    /**
     * 가공 완료 → {@code DONE} 확정 (작업명세 §4.4).
     *
     * <p><b>멱등은 상태 가드로 한다.</b> {@code event_id} SETNX 를 두지 않는 이유는
     * {@code DONE} 인 잡에 다시 오면 할 일이 없다는 사실이 이미 행에 적혀 있기
     * 때문이다 — 같은 판단을 Redis 에 한 벌 더 두면 둘이 어긋날 자리가 생긴다.
     *
     * <p>MOTIF 는 <b>결과 행 → {@code DONE} → 발행</b> 순서다(§13.3 완료 순서, 249).
     * 한 트랜잭션이라 행 생성이 실패하면 {@code DONE} 도 푸시도 없다 — 그래서
     * {@code status=DONE} 인 MOTIF 응답에는 {@code resultSeq} 가 항상 있다.
     */
    @Transactional
    public void packaged(CompositePackagedEvent event) {
        CompositeJob job = compositeJobRepository.findById(event.sourceSeq()).orElse(null);
        if (job == null) {
            log.warn("composite.packaged — 잡이 없다: jobSeq={} mediaJobUuid={}",
                    event.sourceSeq(), event.mediaJobUuid());
            return;
        }
        if (job.isDone()) {
            log.info("composite.packaged 재전달 — 이미 DONE: jobUuid={}", job.getJobUuid());
            return;
        }
        // 가공이 도는 사이 어드민이 챌린지를 지웠을 수 있다. 지워진 챌린지의 결과를
        // DONE 으로 두면 §13.3 이 재생 주소를 내주는데 그 챌린지는 앱에 없다
        if (job.getJobType() == JobType.CHALLENGE
                && !challengeRepository.existsByChallengeSeqAndDeletedFalse(job.getChallengeSeq())) {
            log.info("composite.packaged — 챌린지가 삭제됐다: jobUuid={} challengeSeq={}",
                    job.getJobUuid(), job.getChallengeSeq());
            fail(job, FAIL_CHALLENGE_DELETED);
            return;
        }

        Long resultSeq = job.getJobType() == JobType.MOTIF
                ? motifResult(job, event)
                : null;
        job.complete(event.masterKey(), event.thumbnailKey());
        publishFinished(job, CompositeFinishedEvent.STATUS_DONE, null, resultSeq);
        log.info("합성 완료 확정: jobUuid={} jobType={} resultSeq={}",
                job.getJobUuid(), job.getJobType(), resultSeq);
    }

    /**
     * 실패 확정 — {@code /failed} 콜백·스윕·챌린지 삭제가 함께 쓴다.
     *
     * <p>이미 {@code DONE} 이면 건드리지 않는다. 결과는 실물이라 뒤늦은 실패 보고보다
     * 우선한다(§5 멱등 표).
     */
    @Transactional
    public void fail(CompositeJob job, String failCode) {
        if (job.isDone()) {
            log.info("실패 보고 무시 — 이미 DONE: jobUuid={} failCode={}",
                    job.getJobUuid(), failCode);
            return;
        }
        job.fail(failCode);
        publishFinished(job, CompositeFinishedEvent.STATUS_FAILED, failCode, null);
        log.info("합성 실패 확정: jobUuid={} failCode={}", job.getJobUuid(), failCode);
    }

    /**
     * MOTIF 보관함 행 (249).
     *
     * <p><b>{@code uq_motif_results_job} 이 최종 방어선이다.</b> 재전달을 상태 가드가
     * 먼저 거르지만, {@code DONE} 전에 두 번 들어오는 경합이 남는다 — 그때 유니크가
     * 막는다. 여기서는 먼저 조회해 있으면 그 seq 를 그대로 쓴다(ON CONFLICT DO NOTHING
     * 과 같은 효과이고, seq 를 돌려받을 수 있다).
     *
     * <p>{@code duration_ms} 는 이벤트 값이 우선이고, app-media 가 못 쟀으면 에셋
     * 길이로 갈음한다(§4.4) — 보관함 목록이 길이를 보여 줘야 해서 NOT NULL 이다.
     */
    private Long motifResult(CompositeJob job, CompositePackagedEvent event) {
        return motifResultRepository.findByJobSeq(job.getJobSeq())
                .map(MotifResult::getResultSeq)
                .orElseGet(() -> {
                    MotifAsset asset = motifAssetRepository.findById(job.getAssetSeq())
                            .orElseThrow(() -> new IllegalStateException(
                                    "잡이 가리키는 에셋이 없다: assetSeq=" + job.getAssetSeq()));
                    Integer durationMs = event.durationMs() != null
                            ? event.durationMs()
                            : asset.getDurationMs();
                    return motifResultRepository.save(MotifResult.of(
                            job.getUserSeq(), asset.getAssetSeq(), asset.getArtistSeq(),
                            job.getJobSeq(), event.masterKey(), event.thumbnailKey(),
                            durationMs)).getResultSeq();
                });
    }

    /** 내부 이벤트만 올린다 — MQ 발행은 커밋 후 {@code CompositeFinishedEventRelay} 가 한다. */
    private void publishFinished(CompositeJob job, String status, String failCode, Long resultSeq) {
        eventPublisher.publishEvent(new CompositeFinishedEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                job.getJobUuid().toString(),
                job.getJobType().name(),
                job.getUserSeq(),
                status,
                failCode,
                resultSeq));
    }
}
