package com.ssafy.motif.coreapi.domain.composite.service;

import com.ssafy.motif.coreapi.domain.composite.CompositeProperties;
import com.ssafy.motif.coreapi.domain.composite.dto.CompositeJobPayload;
import com.ssafy.motif.coreapi.domain.composite.dto.WorkerDoneRequest;
import com.ssafy.motif.coreapi.domain.media.MediaKeys;
import com.ssafy.motif.coreapi.global.filter.RequestIdFilter;
import com.ssafy.motif.coreapi.global.media.MediaObjectStore;
import com.ssafy.motif.domain.core.asset.MotifAssetRepository;
import com.ssafy.motif.domain.core.challenge.ChallengeParticipant;
import com.ssafy.motif.domain.core.challenge.ChallengeParticipantRepository;
import com.ssafy.motif.domain.core.challenge.ChallengeRepository;
import com.ssafy.motif.domain.core.job.CompositeJob;
import com.ssafy.motif.domain.core.job.CompositeJobRepository;
import com.ssafy.motif.domain.core.job.JobType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
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

    /**
     * 무한 루프 방지 상한 (§4.7). 사용자의 수동 재시도(§13.4)에는 상한이 없다.
     *
     * <p><b>검사는 접수를 해 본 뒤에 한다.</b> 접수 전에 보면 §13.4 로 {@code attempt} 가
     * 이 값에 닿은 잡이 <b>AI 에 한 번도 가 보지 못하고</b> 닫힌다 — 사용자에게는 "다시
     * 시도" 버튼이 먹통으로 보인다. 수동 재시도와 자동 재접수가 칼럼 하나를 나눠 쓰는
     * 구조라, 순서로 가른다.
     */
    private static final int MAX_ATTEMPT = 3;

    static final String FAIL_STUCK = "STUCK";

    private final CompositeJobRepository compositeJobRepository;
    private final ChallengeRepository challengeRepository;
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
     *
     * <p><b>접수 URL 이 없으면 아무것도 하지 않는다.</b> {@code attempt} 를 올리지 않는
     * 것이 핵심이다 — 올리면 부를 곳도 없는 잡이 3틱 만에 전부 {@code STUCK} 으로 닫히고
     * 사용자에게 실패 푸시가 나간다. AI 주소는 접수 서비스가 준비되면 들어오는 값이라
     * (작업명세 §8 #4 미결) 그때까지 잡을 <b>기다리게</b> 두는 것이 맞다.
     */
    private void resubmitQueued() {
        Instant before = Instant.now().minus(properties.requeueAfter());
        List<CompositeJob> stuck = compositeJobRepository.findStuckQueued(
                before, PageRequest.of(0, BATCH_SIZE));
        if (stuck.isEmpty()) {
            return;
        }
        if (!properties.hasSubmitUrl()) {
            // 대기 중인 잡이 있을 때만 남긴다 — 매 틱 찍으면 하루 1440줄이 되고,
            // 정작 잡이 밀리기 시작한 순간이 그 안에 묻힌다
            log.warn("motif.composite.submit-url 이 비어 있다 — 재접수를 건너뛴다({}건 대기). "
                    + "attempt 를 올리지 않으므로 STUCK 으로 닫히지 않는다", stuck.size());
            return;
        }
        for (CompositeJob job : stuck) {
            MDC.put(RequestIdFilter.MDC_KEY, requestIdOf(job));
            try {
                CompositeJobPayload payload = transactionTemplate.execute(status -> {
                    CompositeJob managed = compositeJobRepository.findById(job.getJobSeq())
                            .orElseThrow();
                    managed.reattempt();
                    return payloadOf(managed);
                });
                log.info("재접수 시도: jobUuid={} attempt={}", job.getJobUuid(), payload.attempt());
                // 접수를 해 보고 나서 상한을 본다 — 순서가 반대면 §13.4 재시도가 막힌다
                if (!submitter.submit(payload) && payload.attempt() >= MAX_ATTEMPT) {
                    log.warn("접수 {}회 실패 — STUCK 으로 닫는다: jobUuid={}",
                            payload.attempt(), job.getJobUuid());
                    completionService.fail(job.getJobUuid(), FAIL_STUCK);
                }
            } catch (RuntimeException e) {
                // 한 잡이 터져도 나머지는 계속 간다 — 다음 틱에 다시 잡힌다
                log.error("재접수 실패: jobUuid={}", job.getJobUuid(), e);
            } finally {
                MDC.remove(RequestIdFilter.MDC_KEY);
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
            MDC.put(RequestIdFilter.MDC_KEY, requestIdOf(job));
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
            } finally {
                MDC.remove(RequestIdFilter.MDC_KEY);
            }
        }
    }

    /**
     * 접수 구간의 추적 ID — {@code jobUuid} 앞 8자.
     *
     * <p><b>디스패처는 스케줄러 스레드라 MDC 가 비어 있다.</b> 채워 주지 않으면
     * {@code CompositeJobSubmitter} 가 {@code X-Request-Id} 를 {@code null} 로 보내고,
     * AI 가 되돌려줄 값이 없어 콜백에서 새 ID 가 생긴다 — 접수와 콜백이 다른 ID 로
     * 갈려 한 잡을 한 ID 로 grep 할 수 없다(AI 서버 명세 §3.1).
     *
     * <p>{@code jobUuid} 에서 따는 이유는 그 값으로도 같은 줄들이 걸리기 때문이다.
     */
    private static String requestIdOf(CompositeJob job) {
        return job.getJobUuid().toString().substring(0, 8);
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
        ChallengeParticipant edited = job.getChallengeParticipantSeq() == null ? null
                : challengeParticipantRepository.findById(job.getChallengeParticipantSeq())
                        .orElseThrow(() -> new IllegalStateException(
                                "잡이 가리키는 슬롯이 없다: " + job.getChallengeParticipantSeq()));
        return CompositeJobPayloads.from(job,
                challengeRepository.findById(job.getChallengeSeq())
                        .orElseThrow(() -> new IllegalStateException(
                                "잡이 가리키는 챌린지가 없다: " + job.getChallengeSeq())),
                edited,
                challengeParticipantRepository
                        .findByChallengeSeqOrderBySlotOrderAsc(job.getChallengeSeq()));
    }
}
