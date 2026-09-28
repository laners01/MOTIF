package com.ssafy.motif.coreapi.domain.composite.service;

import com.ssafy.motif.common.error.CommonErrorCode;
import com.ssafy.motif.common.error.FieldErrorResponse;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.admin.media.AdminMediaProperties;
import com.ssafy.motif.coreapi.domain.asset.AssetErrorCode;
import com.ssafy.motif.coreapi.domain.asset.dto.MyAccess;
import com.ssafy.motif.coreapi.domain.asset.service.AssetQueryService;
import com.ssafy.motif.coreapi.domain.challenge.ChallengeErrorCode;
import com.ssafy.motif.coreapi.domain.composite.dto.CompositeJobCreateRequest;
import com.ssafy.motif.coreapi.domain.composite.dto.CompositeJobCreateResponse;
import com.ssafy.motif.coreapi.domain.composite.dto.CompositeJobRequestBody;
import com.ssafy.motif.coreapi.domain.composite.dto.CompositeJobRequestResponse;
import com.ssafy.motif.coreapi.domain.composite.dto.CompositeJobRetryResponse;
import com.ssafy.motif.coreapi.domain.composite.CompositeErrorCode;
import com.ssafy.motif.coreapi.domain.media.MediaKeys;
import com.ssafy.motif.coreapi.global.media.MediaObjectStore;
import com.ssafy.motif.domain.core.challenge.ChallengeParticipant;
import com.ssafy.motif.domain.core.challenge.ChallengeParticipantRepository;
import com.ssafy.motif.domain.core.challenge.ChallengeRepository;
import com.ssafy.motif.domain.core.job.CompositeJob;
import com.ssafy.motif.domain.core.job.CompositeJobRepository;
import com.ssafy.motif.domain.core.job.JobStatus;
import com.ssafy.motif.domain.core.job.JobType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 합성 잡 생성 (API 명세 §13.1) — 파이프라인의 1단계.
 *
 * <p><b>CHALLENGE 와 MOTIF 가 같은 엔드포인트다</b>(S15P21D104-186·246). 검증만 갈리고
 * 그 뒤(원본 키·presign·응답)는 완전히 같다 — 따로 만들면 키 규약과 만료가 두 벌이 된다.
 *
 * <p><b>업로드는 §13.1 에서 끝나지 않는다.</b> 잡은 {@code UPLOADED} 로 만들어지지만 그것은
 * "URL 을 내줬다" 는 뜻이고, 실제 업로드 여부는 §13.2 가 S3 HEAD 로 확인한다 — 바이트가
 * 서버를 지나지 않아 §13.1 에서는 알 방법이 없다({@code CompositeJob.create} javadoc).
 *
 * <p><b>외부 HTTP 를 부르지 않는다.</b> AI 접수는 디스패처(S15P21D104-537)가 {@code QUEUED}
 * 행을 집어 커밋 뒤에 보낸다 — 요청 스레드에서 부르면 커밋 전에 콜백이 도착해 404 를
 * 받는다(작업명세 §7 함정 2). 여기가 하는 일은 상태를 {@code QUEUED} 로 두는 것까지다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CompositeJobCommandService {

    /** 촬영본은 폰이 만든 MP4 다 — 종류가 갈리지 않아 서버가 고정한다(키도 {@code .mp4}). */
    private static final String RAW_CONTENT_TYPE = "video/mp4";

    private final CompositeJobRepository compositeJobRepository;
    private final ChallengeRepository challengeRepository;
    private final ChallengeParticipantRepository participantRepository;
    private final AssetQueryService assetQueryService;
    private final MediaObjectStore objectStore;
    /**
     * 영상 presign 조건 — <b>어드민 업로드와 같은 한도를 쓴다</b>(기본안).
     *
     * <p>사용자 촬영본만의 한도가 아직 정해지지 않았고, 값을 새로 만들면 configmap 에
     * 같은 뜻의 칸이 둘이 된다. 촬영본 전용 한도가 생기면 그때 가른다.
     */
    private final AdminMediaProperties mediaProperties;

    /**
     * §13.1 잡 생성 + 촬영본 presigned PUT 발급.
     *
     * <p>형태 위반(CHALLENGE 인데 {@code assetSeq} 등)은 400 {@code INVALID_PARAMETER} 이고
     * {@code data} 에 필드 오류 배열을 싣는다 — {@code @Valid} 실패와 같은 봉투다.
     *
     * <p><b>presign 을 먼저 하지 않는다.</b> 키에 {@code jobUuid} 가 들어가므로 순서는
     * 잡 저장 → 서명이다. 서명이 터지면 트랜잭션째 롤백돼 쓸 수 없는 잡 행이 남지 않는다.
     *
     * @throws BusinessException 400 {@code INVALID_PARAMETER} · 404 {@code CHALLENGE_NOT_FOUND} /
     *                           {@code EDITED_CHALLENGE_NOT_FOUND} / {@code ASSET_NOT_FOUND} ·
     *                           403 {@code ASSET_ACCESS_DENIED}
     */
    public CompositeJobCreateResponse create(Integer userSeq, CompositeJobCreateRequest request) {
        List<FieldErrorResponse> shapeErrors = request.shapeErrors();
        if (!shapeErrors.isEmpty()) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER,
                    "jobType 형태 불일치: "
                            + shapeErrors.stream().map(FieldErrorResponse::field).toList(),
                    shapeErrors);
        }

        verifySize(request.contentLength());

        UUID jobUuid = UUID.randomUUID();
        String rawKey = MediaKeys.forRawUpload(jobUuid.toString());
        CompositeJob job = switch (request.jobType()) {
            case CHALLENGE -> CompositeJob.create(jobUuid, JobType.CHALLENGE, userSeq,
                    request.challengeSeq(), null,
                    challengeParticipantSeq(request), rawKey);
            case MOTIF -> CompositeJob.create(jobUuid, JobType.MOTIF, userSeq,
                    null, requireAssetAccess(userSeq, request.assetSeq()), null, rawKey);
        };
        compositeJobRepository.save(job);

        Duration ttl = mediaProperties.video().presignTtl();
        return new CompositeJobCreateResponse(
                jobUuid,
                // 클라이언트가 선언한 크기를 그대로 서명에 박는다 — 상한값을 넣으면
                // 그 크기 파일만 올라간다(verify 참고)
                objectStore.presignPut(rawKey, RAW_CONTENT_TYPE, request.contentLength(), ttl),
                ttl.toSeconds());
    }

    /**
     * 크기 상한 확인 — <b>발급 전에</b> 본다 (§13.1).
     *
     * <p><b>왜 클라이언트가 크기를 보내야 하나.</b> SigV4 에서 서명된
     * {@code Content-Length} 는 "이하" 가 아니라 <b>정확히 일치</b>다. 서버가 상한값을
     * 대신 박으면 그 크기(300MB)짜리 파일만 올라가고 나머지는 전부 403
     * {@code SignatureDoesNotMatch} 다 — 2026-09-19 에 실제로 그 상태였다.
     * 어드민 업로드({@code AdminMediaService})·프로필 업로드({@code UserProfileImageService})가
     * 처음부터 이 방식이었다.
     *
     * <p>여기서 통과한 값이 <b>서명에 박혀</b> S3 에서 한 번 더 강제된다 — 여기서만
     * 막으면 발급된 URL 로 다른 크기를 올릴 수 있다.
     *
     * <p>팬 API 라 §1.5 공통 {@code INVALID_PARAMETER} 다 — 어드민의
     * {@code MEDIA_TYPE_NOT_ALLOWED} 를 팬 앱 응답으로 내보내지 않는다.
     */
    private void verifySize(long contentLength) {
        long maxBytes = mediaProperties.video().maxSize().toBytes();
        if (contentLength > maxBytes) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER,
                    "크기 상한 초과: contentLength=%d max=%d".formatted(contentLength, maxBytes),
                    Map.of("maxBytes", maxBytes));
        }
    }

    /**
     * §13.2 합성 요청 — {@code UPLOADED → QUEUED}. <b>202 Accepted</b>.
     *
     * <p>판정 순서는 명세의 서버 처리 순서 그대로다 — ① S3 원본 확인 ② 상태 전이.
     * 업로드가 실제로 끝났는지는 HEAD 로만 알 수 있다(바이트가 서버를 지나지 않는다).
     *
     * <p>{@code correctionEnabled} 는 CHALLENGE 만 본문에서 받는다. MOTIF 는 보정을 쓰지
     * 않아 {@code false} 고정이고, 값이 와도 무시한다(§13.2 · 247).
     *
     * @throws BusinessException 404 {@code JOB_NOT_FOUND} · 409 {@code RAW_NOT_UPLOADED} /
     *                           {@code JOB_NOT_REQUESTABLE}
     */
    public CompositeJobRequestResponse request(Integer userSeq, UUID jobUuid,
                                               CompositeJobRequestBody body) {
        CompositeJob job = ownedJob(userSeq, jobUuid);
        if (!objectStore.exists(job.getRawVideoKey())) {
            throw new BusinessException(CompositeErrorCode.RAW_NOT_UPLOADED,
                    "rawVideoKey: " + job.getRawVideoKey());
        }
        if (job.getStatus() != JobStatus.UPLOADED) {
            throw new BusinessException(CompositeErrorCode.JOB_NOT_REQUESTABLE,
                    "status: " + job.getStatus());
        }
        job.queue(job.getJobType() == JobType.CHALLENGE
                && body != null && body.correctionEnabledOrFalse());
        return new CompositeJobRequestResponse(jobUuid, JobStatus.QUEUED);
    }

    /**
     * §13.4 재시도 — {@code FAILED → QUEUED}. <b>202 Accepted</b>.
     *
     * <p><b>같은 행을 재큐한다.</b> 새 잡을 만들지 않으므로 {@code jobUuid} 가 유지되고,
     * 결과 프리픽스도 그 uuid 라 재합성이 같은 자리를 덮어쓴다(멱등).
     *
     * <p><b>여기서 AI 를 부르지 않는다.</b> 상태를 {@code QUEUED} 로 되돌리는 것까지가
     * 전부고, 재접수는 디스패처(537)가 다음 틱에 같은 조립기로 한다 — §13.2 와 같은
     * 이유다(요청 스레드에서 외부 HTTP 를 부르면 커밋 전에 콜백이 와서 404 를 받는다).
     *
     * <p>판정 순서는 명세의 실패표 순서다 — ① 상태 ② 원본 존재. 상태를 먼저 보는 것은
     * {@code DONE} 인 잡에 S3 왕복을 쓰지 않으려는 것이고, 무엇보다 원본이 수명주기로
     * 지워진 <b>성공한</b> 잡에 410 을 주면 "다시 촬영하라" 는 엉뚱한 안내가 된다.
     *
     * <p><b>시도 상한이 없다</b>(기본안). 디스패처의 {@code attempt < 3} 은 접수 실패
     * 자동 재큐의 무한 루프를 막는 장치라 별개다 — 사용자가 직접 누르는 재시도까지
     * 막을 이유가 없다(§13.4 트레이드오프).
     *
     * @throws BusinessException 404 {@code JOB_NOT_FOUND} · 409 {@code JOB_NOT_RETRYABLE} ·
     *                           410 {@code RAW_VIDEO_EXPIRED}
     */
    public CompositeJobRetryResponse retry(Integer userSeq, UUID jobUuid) {
        CompositeJob job = ownedJob(userSeq, jobUuid);
        if (job.getStatus() != JobStatus.FAILED) {
            throw new BusinessException(CompositeErrorCode.JOB_NOT_RETRYABLE,
                    "status: " + job.getStatus());
        }
        // 410 은 "있었지만 이제 없다" 다 — raw/ 수명주기가 지웠다. 클라이언트는 재시도
        // 버튼을 숨기고 재촬영을 안내한다(§13.4)
        if (!objectStore.exists(job.getRawVideoKey())) {
            throw new BusinessException(CompositeErrorCode.RAW_VIDEO_EXPIRED,
                    "rawVideoKey: " + job.getRawVideoKey());
        }
        job.retry();
        return new CompositeJobRetryResponse(jobUuid, JobStatus.QUEUED, job.getAttempt());
    }

    /**
     * 내 잡 — <b>타인 잡도 404 다</b>(§1.8 존재 은닉).
     *
     * <p>403 으로 가르면 남의 {@code jobUuid} 를 넣어 보고 존재 여부를 읽을 수 있다.
     */
    private CompositeJob ownedJob(Integer userSeq, UUID jobUuid) {
        return compositeJobRepository.findByJobUuid(jobUuid)
                .filter(job -> job.isOwnedBy(userSeq))
                .orElseThrow(() -> new BusinessException(
                        CompositeErrorCode.JOB_NOT_FOUND, "jobUuid: " + jobUuid));
    }

    /**
     * CHALLENGE — 챌린지가 살아 있는지 + 고른 슬롯에 편집본이 있는지.
     *
     * <p><b>슬롯은 필수다</b>(2026-09-22). 합성은 언제나 슬롯 한 자리를 대체하는 것이고,
     * 원본 위에 얹는 무교체 합성은 없어졌다 — 그래서 여기서 {@code null} 이 나오는 길이
     * 없다. 칸이 비었으면 {@code shapeErrors} 가 이미 400 으로 걸렀다.
     *
     * <p><b>슬롯이 그 챌린지 것인지 대조한다</b>(DB SPEC §37 반정규화 정합). 잡 행이
     * {@code challenge_seq} 와 {@code challenge_participant_seq} 를 함께 들고 있어
     * 둘이 어긋나면 합성 베이스와 챌린지가 다른 잡이 만들어진다 — FK 는 각자만 본다.
     *
     * <p>{@code EDITED_CHALLENGE_NOT_FOUND} 는 세 경우다 — 슬롯이 없거나, 다른 챌린지의
     * 슬롯이거나, 슬롯은 맞는데 어드민이 편집본을 아직 안 올렸거나. 셋 다 같은 코드인
     * 이유는 §1.8 이다: 남의 챌린지 슬롯 seq 를 넣어 보고 존재를 읽을 수 없어야 한다.
     *
     * @return 슬롯 seq — 언제나 값이 있다
     */
    private Integer challengeParticipantSeq(CompositeJobCreateRequest request) {
        Integer challengeSeq = request.challengeSeq();
        if (!challengeRepository.existsByChallengeSeqAndDeletedFalse(challengeSeq)) {
            throw new BusinessException(ChallengeErrorCode.CHALLENGE_NOT_FOUND,
                    "challengeSeq: " + challengeSeq);
        }
        Integer participantSeq = request.challengeParticipantSeq();
        return participantRepository.findById(participantSeq)
                .filter(slot -> slot.getChallengeSeq().equals(challengeSeq))
                .filter(ChallengeParticipant::hasEdited)
                .map(ChallengeParticipant::getChallengeParticipantSeq)
                .orElseThrow(() -> new BusinessException(
                        ChallengeErrorCode.EDITED_CHALLENGE_NOT_FOUND,
                        "challengeSeq: " + challengeSeq
                                + ", challengeParticipantSeq: " + participantSeq));
    }

    /**
     * MOTIF — <b>선결제 원칙</b>이라 권한 없이는 촬영을 시작할 수 없다 (§13.1).
     *
     * <p>판정은 {@code AssetQueryService.myAccess} 하나다 — 숍·상세와 같은 규칙이라
     * "숍에서는 열려 보이는데 촬영은 막히는" 순간이 없다. 판매 중단 에셋이어도 권한이
     * 있으면 통과한다(DB §37).
     *
     * @return 검증을 통과한 {@code assetSeq} — 호출부가 그대로 잡에 넣는다
     */
    private Integer requireAssetAccess(Integer userSeq, Integer assetSeq) {
        if (assetQueryService.myAccess(userSeq, assetSeq) == MyAccess.NONE) {
            throw new BusinessException(AssetErrorCode.ASSET_ACCESS_DENIED,
                    "assetSeq: " + assetSeq);
        }
        return assetSeq;
    }
}
