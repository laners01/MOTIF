package com.ssafy.motif.coreapi.domain.composite.service;

import com.ssafy.motif.coreapi.domain.composite.dto.CompositeJobPayload;
import com.ssafy.motif.coreapi.domain.media.MediaKeys;
import com.ssafy.motif.domain.core.asset.MotifAsset;
import com.ssafy.motif.domain.core.challenge.Challenge;
import com.ssafy.motif.domain.core.challenge.ChallengeParticipant;
import com.ssafy.motif.domain.core.job.CompositeJob;

/**
 * 잡 행 → AI 접수 페이로드 (AI 서버 명세 §4).
 *
 * <p><b>조립 지점은 여기 하나다.</b> §13.2·§13.4·스윕이 각자 만들면 재시도 때 베이스 키나
 * 슬롯이 처음과 달라질 수 있는데, 결과 키는 {@code jobUuid} 로 멱등해 <b>같은 자리에 다른
 * 합성</b>이 덮인다.
 *
 * <p><b>순수 계산이다</b> — 조회도 부수효과도 없다. 필요한 행은 부르는 쪽이 읽어 넘긴다
 * ({@code SlotRules} 와 같은 모양). 덕분에 명세의 JSON 과 필드 단위로 대조하는 단위
 * 테스트가 DB 없이 돈다.
 */
public final class CompositeJobPayloads {

    private CompositeJobPayloads() {
    }

    /**
     * CHALLENGE — 베이스는 <b>슬롯 인물이 지워진 편집본</b>이다.
     *
     * <p><b>교체 슬롯은 언제나 있다</b>(2026-09-22). 합성은 슬롯 한 자리를 대체하는 것이고
     * 원본 위에 얹는 무교체 합성은 없어졌다 — §13.1 이 칸을 필수로 받으므로 {@code edited}
     * 가 {@code null} 로 오는 길이 없다.
     *
     * <p>{@code participantMask} 는 잡 행에 없다. 교체 대상 슬롯이 곧 그 값이라
     * {@code edited.participantMask()}(= {@code 1 << slotOrder})를 슬롯 안에 싣는다.
     *
     * @param edited 교체 대상 슬롯
     */
    public static CompositeJobPayload from(CompositeJob job, Challenge challenge,
                                           ChallengeParticipant edited) {
        return payload(job,
                MediaKeys.sourceMp4Of(edited.getEditedChallengeKey()),
                new CompositeJobPayload.Slot(edited.participantMask(), anchor(edited)),
                challenge.getDurationMs());
    }

    /**
     * MOTIF — 베이스는 구매·패스로 연 에셋 영상이다.
     *
     * <p>슬롯이 없다. 교체할 인물이라는 개념 자체가 챌린지의 것이라 {@code slot} 이
     * {@code null} 이다(S15P21D104-247).
     */
    public static CompositeJobPayload from(CompositeJob job, MotifAsset asset) {
        return payload(job, MediaKeys.sourceMp4Of(asset.getVideoKey()),
                null, asset.getDurationMs());
    }

    private static CompositeJobPayload payload(CompositeJob job, String baseVideoKey,
                                               CompositeJobPayload.Slot slot,
                                               int baseDurationMs) {
        String jobType = job.getJobType().name();
        return new CompositeJobPayload(
                job.getJobUuid(),
                jobType,
                job.getAttempt(),
                job.getRawVideoKey(),
                baseVideoKey,
                MediaKeys.forWorkerOutput(jobType, job.getJobUuid().toString()),
                job.isCorrectionEnabled(),
                slot,
                baseDurationMs);
    }

    private static CompositeJobPayload.Anchor anchor(ChallengeParticipant participant) {
        ChallengeParticipant.Anchor anchor = participant.anchorOrNull();
        return anchor == null ? null
                : new CompositeJobPayload.Anchor(anchor.getX(), anchor.getY(), anchor.getR());
    }
}
