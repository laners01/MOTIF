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
import com.ssafy.motif.coreapi.domain.media.MediaKeys;
import com.ssafy.motif.coreapi.global.media.MediaObjectStore;
import com.ssafy.motif.domain.core.challenge.ChallengeRepository;
import com.ssafy.motif.domain.core.challenge.EditedChallenge;
import com.ssafy.motif.domain.core.challenge.EditedChallengeRepository;
import com.ssafy.motif.domain.core.job.CompositeJob;
import com.ssafy.motif.domain.core.job.CompositeJobRepository;
import com.ssafy.motif.domain.core.job.JobType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * 합성 잡 생성 (API 명세 §13.1) — 파이프라인의 1단계.
 *
 * <p><b>CHALLENGE 와 MOTIF 가 같은 엔드포인트다</b>(S15P21D104-186·246). 검증만 갈리고
 * 그 뒤(원본 키·presign·응답)는 완전히 같다 — 따로 만들면 키 규약과 만료가 두 벌이 된다.
 *
 * <p><b>업로드는 여기서 끝나지 않는다.</b> 잡은 {@code UPLOADED} 로 만들어지지만 그것은
 * "URL 을 내줬다" 는 뜻이고, 실제 업로드 여부는 §13.2 가 S3 HEAD 로 확인한다 — 바이트가
 * 서버를 지나지 않아 여기서는 알 방법이 없다({@code CompositeJob.create} javadoc).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CompositeJobCommandService {

    private final CompositeJobRepository compositeJobRepository;
    private final ChallengeRepository challengeRepository;
    private final EditedChallengeRepository editedChallengeRepository;
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

        UUID jobUuid = UUID.randomUUID();
        String rawKey = MediaKeys.forRawUpload(jobUuid.toString());
        CompositeJob job = switch (request.jobType()) {
            case CHALLENGE -> CompositeJob.create(jobUuid, JobType.CHALLENGE, userSeq,
                    request.challengeSeq(), null,
                    editedChallengeSeq(request), rawKey);
            case MOTIF -> CompositeJob.create(jobUuid, JobType.MOTIF, userSeq,
                    null, requireAssetAccess(userSeq, request.assetSeq()), null, rawKey);
        };
        compositeJobRepository.save(job);

        Duration ttl = mediaProperties.video().presignTtl();
        return new CompositeJobCreateResponse(
                jobUuid,
                objectStore.presignPut(rawKey, "video/mp4",
                        mediaProperties.video().maxSize().toBytes(), ttl),
                ttl.toSeconds());
    }

    /**
     * CHALLENGE — 챌린지가 살아 있는지 + 고른 조합의 편집본이 있는지.
     *
     * <p>{@code participantMask} 가 0·생략이면 무교체라 편집본을 찾지 않고 {@code null} 이다
     * (§13.1 — 원본 위 합성). 그 조합에는 행 자체가 없다.
     *
     * <p><b>{@code EDITED_CHALLENGE_NOT_FOUND} 는 운영 전제가 깨졌을 때만 나온다</b> —
     * 어드민이 {@code 1 … 2ⁿ−1} 전조합을 등록하기로 했기 때문이다(§7.2). 사용자가 이걸
     * 보면 촬영 직전에 막힌 것이다.
     *
     * @return 편집본 seq. 무교체면 {@code null}
     */
    private Integer editedChallengeSeq(CompositeJobCreateRequest request) {
        Integer challengeSeq = request.challengeSeq();
        if (!challengeRepository.existsByChallengeSeqAndDeletedFalse(challengeSeq)) {
            throw new BusinessException(ChallengeErrorCode.CHALLENGE_NOT_FOUND,
                    "challengeSeq: " + challengeSeq);
        }
        if (request.isNoReplacement()) {
            return null;
        }
        return editedChallengeRepository
                .findByChallengeSeqAndParticipantMask(challengeSeq, request.participantMask())
                .map(EditedChallenge::getEditedChallengeSeq)
                .orElseThrow(() -> new BusinessException(
                        ChallengeErrorCode.EDITED_CHALLENGE_NOT_FOUND,
                        "challengeSeq: " + challengeSeq
                                + ", participantMask: " + request.participantMask()));
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
