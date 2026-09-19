package com.ssafy.motif.coreapi.domain.composite.service;

import com.ssafy.motif.coreapi.domain.composite.CompositeProperties;
import com.ssafy.motif.coreapi.domain.composite.dto.CompositeJobPayload;
import com.ssafy.motif.coreapi.domain.composite.dto.WorkerDoneRequest;
import com.ssafy.motif.coreapi.domain.media.MediaKeys;
import com.ssafy.motif.coreapi.global.media.MediaObjectStore;
import com.ssafy.motif.domain.core.asset.MotifAssetRepository;
import com.ssafy.motif.domain.core.challenge.ChallengeParticipantRepository;
import com.ssafy.motif.domain.core.challenge.ChallengeRepository;
import com.ssafy.motif.domain.core.challenge.EditedChallenge;
import com.ssafy.motif.domain.core.challenge.EditedChallengeRepository;
import com.ssafy.motif.domain.core.job.CompositeJob;
import com.ssafy.motif.domain.core.job.CompositeJobRepository;
import com.ssafy.motif.domain.core.job.JobType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 합성 잡 디스패처 (작업명세 §4.7 — S15P21D104-537).
 *
 * <p><b>큐가 없어진 자리를 메운다.</b> AI 경계가 RabbitMQ 에서 HTTP 로 바뀌면서
 * 재전달해 주는 브로커가 사라졌다 — 접수 실패(쿼터·AI 장애)와 콜백 없는 죽음
 * (task-timeout·OOM·가공 실패)을 되살리는 장치가 이것 하나다. <b>연동 첫날 전에
 * 있어야 한다.</b>
 *
 * <p>잡 행이 아웃박스 구실을 한다(ADR-0005 — "아웃박스 염가판"). 별도 큐나 테이블을
 * 두지 않고 {@code composite_jobs.status} + {@code mod_dttm} 만으로 재개 지점을 찾는다.
 *
 * <p><b>ShedLock 을 쓰지 않는다.</b> core-api 는 단일 레플리카다({@code RankingConfig}
 * 와 같은 전제). 레플리카가 늘면 같은 잡을 둘이 접수해 GPU 시간만 두 번 쓰는데,
 * 결과 키가 멱등이라 데이터는 깨지지 않는다 — 그때 가서 잠금을 넣는다.
 *
 * <p><b>한 잡의 실패가 틱 전체를 멈추지 않는다.</b> 잡마다 트랜잭션과 try 를 따로
 * 둔다 — 한 건이 터져 나머지 19건이 밀리면 그게 곧 장애다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompositeJobDispatcher {

    /** 한 틱 상한 (§4.7). 밀린 잡이 많아도 한 번에 GPU 를 몰아치지 않는다. */
    private static final int BATCH_SIZE = 20;

    /** 무한 루프 방지 상한 (§4.7). 사용자의 수동 재시도(§13.4)에는 상한이 없다. */
    private static final int MAX_ATTEMPT = 3;

    static final String FAIL_STUCK = "STUCK";

    private final CompositeJobRepository compositeJobRepository;
    private final ChallengeRepository challengeRepository;
    private final EditedChallengeRepository editedChallengeRepository;
    private final ChallengeParticipantRepository challengeParticipantRepository;
    private final MotifAssetRepository motifAssetRepository;
    private final CompositeJobSubmitter submitter;
    private final CompositeJobCompletionService completionService;
    private final MediaObjectStore objectStore;
    private final CompositeProperties properties;
    private final TransactionTemplate transactionTemplate;

    /** 한 틱. 등록은 {@code CompositeDispatcherConfig} 가 한다. */
    public void sweep() {
        resubmitQueued();
        recoverProcessing();
    }

    /**
     * ① 접수되지 못한 {@code QUEUED} 를 다시 민다.
     *
     * <p>{@code attempt} 를 먼저 올리고 <b>커밋한 뒤</b> 접수한다. 순서가 반대면 접수가
     * 성공해 {@code markSubmitted()} 가 {@code REQUIRES_NEW} 로 도는 사이 바깥
     * 트랜잭션이 아직 열려 있어, 두 트랜잭션이 같은 행을 두고 엇갈린다.
     */
    private void resubmitQueued() {
        Instant before = Instant.now().minus(properties.requeueAfter());
        List<CompositeJob> stuck = compositeJobRepository.findStuckQueued(
                before, PageRequest.of(0, BATCH_SIZE));
        for (CompositeJob job : stuck) {
            try {
                if (job.getAttempt() >= MAX_ATTEMPT) {
                    log.warn("접수 {}회 실패 — STUCK 으로 닫는다: jobUuid={}",
                            job.getAttempt(), job.getJobUuid());
                    completionService.fail(job.getJobUuid(), FAIL_STUCK);
                    continue;
                }
                CompositeJobPayload payload = transactionTemplate.execute(status -> {
                    CompositeJob managed = compositeJobRepository.findById(job.getJobSeq())
                            .orElseThrow();
                    managed.reattempt();
                    return payloadOf(managed);
                });
                log.info("재접수 시도: jobUuid={} attempt={}", job.getJobUuid(), payload.attempt());
                submitter.submit(payload);
            } catch (RuntimeException e) {
                // 한 잡이 터져도 나머지는 계속 간다 — 다음 틱에 다시 잡힌다
                log.error("재접수 실패: jobUuid={}", job.getJobUuid(), e);
            }
        }
    }

    /**
     * ② 콜백이 오지 않은 {@code PROCESSING} — <b>결과가 있으면 살린다</b>.
     *
     * <p>AI 가 S3 에 결과를 올리고 콜백만 유실된 경우가 있다(HTTP 3회 재시도를 다 쓰고
     * 포기, 또는 우리 쪽 일시 장애). 그때 {@code STUCK} 으로 닫으면 <b>이미 만들어진
     * 결과물을 버리고 사용자에게 실패 푸시를 보낸다.</b> 그래서 먼저 S3 를 본다.
     *
     * <p>있으면 {@code /done} 과 같은 경로를 탄다 — {@link CompositeJobCompletionService#aiDone}
     * 을 그대로 부른다. 키 대조·{@code job.media} 발행이 콜백과 한 코드다.
     */
    private void recoverProcessing() {
        Instant before = Instant.now().minus(properties.processingStuckAfter());
        List<CompositeJob> stuck = compositeJobRepository.findStuckProcessing(
                before, PageRequest.of(0, BATCH_SIZE));
        for (CompositeJob job : stuck) {
            UUID jobUuid = job.getJobUuid();
            String resultKey = MediaKeys.forWorkerOutput(job.getJobType().name(), jobUuid.toString());
            try {
                if (objectStore.exists(resultKey)) {
                    log.info("콜백 유실로 본다 — S3 에 결과가 있다: jobUuid={} key={}",
                            jobUuid, resultKey);
                    completionService.aiDone(jobUuid,
                            new WorkerDoneRequest(resultKey, null, null, "dispatcher-recovery"));
                    continue;
                }
                log.warn("정체 — 결과도 없다. STUCK 으로 닫는다: jobUuid={}", jobUuid);
                completionService.fail(jobUuid, FAIL_STUCK);
            } catch (RuntimeException e) {
                log.error("정체 잡 처리 실패: jobUuid={}", jobUuid, e);
            }
        }
    }

    /**
     * 잡 행에서 접수 페이로드를 조립한다 — 읽기는 여기, 계산은 {@link CompositeJobPayloads}.
     *
     * <p>조립기가 순수 함수라 필요한 행을 부르는 쪽이 읽어 넘긴다({@code SlotRules} 와
     * 같은 모양). §13.2·§13.4·스윕이 <b>같은 조립기</b>를 써야 재시도 때 베이스 키나
     * 슬롯이 처음과 달라지지 않는다.
     */
    private CompositeJobPayload payloadOf(CompositeJob job) {
        if (job.getJobType() == JobType.MOTIF) {
            return CompositeJobPayloads.from(job, motifAssetRepository.findById(job.getAssetSeq())
                    .orElseThrow(() -> new IllegalStateException(
                            "잡이 가리키는 에셋이 없다: assetSeq=" + job.getAssetSeq())));
        }
        EditedChallenge edited = job.getEditedChallengeSeq() == null ? null
                : editedChallengeRepository.findById(job.getEditedChallengeSeq())
                        .orElseThrow(() -> new IllegalStateException(
                                "잡이 가리키는 편집본이 없다: " + job.getEditedChallengeSeq()));
        return CompositeJobPayloads.from(job,
                challengeRepository.findById(job.getChallengeSeq())
                        .orElseThrow(() -> new IllegalStateException(
                                "잡이 가리키는 챌린지가 없다: " + job.getChallengeSeq())),
                edited,
                challengeParticipantRepository
                        .findByIdChallengeSeqOrderByParticipantMask(job.getChallengeSeq()));
    }
}
