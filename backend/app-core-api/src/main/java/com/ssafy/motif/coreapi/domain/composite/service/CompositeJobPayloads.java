package com.ssafy.motif.coreapi.domain.composite.service;

import com.ssafy.motif.coreapi.domain.composite.dto.CompositeJobPayload;
import com.ssafy.motif.coreapi.domain.media.MediaKeys;
import com.ssafy.motif.domain.core.asset.MotifAsset;
import com.ssafy.motif.domain.core.challenge.Challenge;
import com.ssafy.motif.domain.core.challenge.ChallengeParticipant;
import com.ssafy.motif.domain.core.challenge.EditedChallenge;
import com.ssafy.motif.domain.core.job.CompositeJob;

import java.util.Comparator;
import java.util.List;

/**
 * 잡 행 → AI 접수 페이로드 (AI 서버 명세 §3.2).
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
     * CHALLENGE — 베이스는 <b>슬롯 인물이 지워진 편집본</b>, 무교체면 원본이다.
     *
     * <p>{@code participantMask} 는 잡 행에 없다. 편집본이 그 조합 자체이므로
     * {@code edited.participantMask} 가 곧 합산값이고, 무교체({@code edited == null})면
     * {@code 0} 이다 — §13.1 이 그 둘을 이미 갈라 놓았다.
     *
     * @param edited       무교체면 {@code null}
     * @param participants 그 챌린지의 <b>전체</b> 슬롯. 교체 대상 걸러내기는 여기서 한다
     */
    public static CompositeJobPayload from(CompositeJob job, Challenge challenge,
                                           EditedChallenge edited,
                                           List<ChallengeParticipant> participants) {
        int mask = edited == null ? 0 : edited.getParticipantMask();
        String baseVideoKey = MediaKeys.sourceMp4Of(
                edited == null ? challenge.getVideoKey() : edited.getVideoKey());
        return payload(job, baseVideoKey, mask, slots(participants, mask), challenge.getDurationMs());
    }

    /**
     * MOTIF — 베이스는 구매·패스로 연 에셋 영상이다.
     *
     * <p>슬롯이 없다. 교체할 인물이라는 개념 자체가 챌린지의 것이라 {@code participantMask}
     * 는 {@code 0}, {@code slots} 는 빈 배열이다(S15P21D104-247).
     */
    public static CompositeJobPayload from(CompositeJob job, MotifAsset asset) {
        return payload(job, MediaKeys.sourceMp4Of(asset.getVideoKey()),
                0, List.of(), asset.getDurationMs());
    }

    private static CompositeJobPayload payload(CompositeJob job, String baseVideoKey, int mask,
                                               List<CompositeJobPayload.Slot> slots,
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
                mask,
                slots,
                baseDurationMs);
    }

    /**
     * 교체 대상 슬롯만, 마스크 오름차순(= 화면 좌→우).
     *
     * <p>{@code mask} 는 비트 합이라 {@code &} 로 고른다 — 슬롯의 {@code participantMask} 는
     * 단일 비트다. 앵커는 <b>그대로</b> 넘긴다. 0017 이전 행은 {@code null} 이고, AI 에는
     * 위치 힌트라 없어도 합성 자체는 돈다.
     */
    private static List<CompositeJobPayload.Slot> slots(List<ChallengeParticipant> participants,
                                                        int mask) {
        if (mask == 0) {
            return List.of();
        }
        return participants.stream()
                .filter(p -> (p.getParticipantMask() & mask) != 0)
                .sorted(Comparator.comparingInt(ChallengeParticipant::getParticipantMask))
                .map(p -> new CompositeJobPayload.Slot(p.getParticipantMask(), anchor(p)))
                .toList();
    }

    private static CompositeJobPayload.Anchor anchor(ChallengeParticipant participant) {
        ChallengeParticipant.Anchor anchor = participant.anchorOrNull();
        return anchor == null ? null
                : new CompositeJobPayload.Anchor(anchor.getX(), anchor.getY(), anchor.getR());
    }
}
