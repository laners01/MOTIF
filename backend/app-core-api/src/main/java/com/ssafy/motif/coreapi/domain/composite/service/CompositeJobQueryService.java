package com.ssafy.motif.coreapi.domain.composite.service;

import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.domain.composite.CompositeErrorCode;
import com.ssafy.motif.coreapi.domain.composite.dto.CompositeJobStatusResponse;
import com.ssafy.motif.coreapi.global.media.MediaUrlResolver;
import com.ssafy.motif.domain.core.job.CompositeJob;
import com.ssafy.motif.domain.core.job.CompositeJobRepository;
import com.ssafy.motif.domain.core.motif.MotifResult;
import com.ssafy.motif.domain.core.motif.MotifResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 잡 상태·결과 조회 (API 명세 §13.3).
 *
 * <p><b>FCM 을 받고 한 번 부르는 자리다. 폴링이 아니다</b>(§1.6) — 앱은 알림을 눌러
 * 들어올 때 이 API 를 1회 호출해 나머지를 받는다. 그래서 응답 하나에 결과 화면이 필요한
 * 것이 전부 들어 있어야 한다.
 *
 * <p>읽기 전용 트랜잭션이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompositeJobQueryService {

    private final CompositeJobRepository compositeJobRepository;
    private final MotifResultRepository motifResultRepository;
    private final MediaUrlResolver mediaUrlResolver;

    /**
     * §13.3 — 공통부 + {@code jobType} 별 분기.
     *
     * <p><b>잡이 실패해도 200 이다.</b> 클라이언트는 {@code status} 로 판단하고
     * {@code failCode} 로 안내한 뒤 "다시 시도"(§13.4)를 노출한다 — 조회 자체가 실패한
     * 것과 합성이 실패한 것은 다른 사건이라 HTTP 로 섞지 않는다.
     *
     * @throws BusinessException 404 {@code JOB_NOT_FOUND} — 잡 없음·<b>타인 잡</b>
     */
    public CompositeJobStatusResponse status(Integer userSeq, UUID jobUuid) {
        CompositeJob job = compositeJobRepository.findByJobUuid(jobUuid)
                .filter(candidate -> candidate.isOwnedBy(userSeq))
                .orElseThrow(() -> new BusinessException(
                        CompositeErrorCode.JOB_NOT_FOUND, "jobUuid: " + jobUuid));

        return switch (job.getJobType()) {
            case CHALLENGE -> challenge(job);
            case MOTIF -> motif(job);
        };
    }

    /**
     * 게시 전 프리뷰라 보호 등급이다 — <b>URL 을 조립하지 않는다</b>.
     *
     * <p>재생목록과 썸네일은 서명이 붙는 방식이 다르다(ADR-0012) — 재생목록은 프리픽스
     * 서명, 썸네일은 단일 파일이다. 둘을 가르는 것도 등급을 정하는 것도
     * {@link MediaUrlResolver} 의 몫이고, 여기서는 {@code DONE} 인지만 본다.
     *
     * <p>{@code DONE} 이 아니면 키가 {@code null} 이다 —
     * {@code ck_composite_jobs_status_shape} 가 DB 에서 강제한다.
     */
    private CompositeJobStatusResponse challenge(CompositeJob job) {
        boolean done = job.isDone();
        return new CompositeJobStatusResponse.Challenge(
                job.getJobUuid(),
                job.getJobType(),
                job.getStatus(),
                job.getAttempt(),
                job.getFailCode(),
                done ? mediaUrlResolver.resolvePlaylist(job.getResultVideoKey()) : null,
                done ? mediaUrlResolver.resolve(job.getResultThumbnailKey()) : null);
    }

    /**
     * URL 없이 {@code resultSeq} 만 — 재생은 §11.6 이 담당한다(R8 확정).
     *
     * <p>{@code DONE} 이면 결과 행이 <b>반드시 있다</b>(§13.3 완료 순서). 없다면 그 불변이
     * 깨진 것이므로 조회를 500 으로 터뜨리지 않고 {@code null} 로 답한다 — 결과 화면이
     * 안 열릴 뿐이고, 원인은 잡 행과 {@code motif_results} 를 대조해야 보인다.
     */
    private CompositeJobStatusResponse motif(CompositeJob job) {
        return new CompositeJobStatusResponse.Motif(
                job.getJobUuid(),
                job.getJobType(),
                job.getStatus(),
                job.getAttempt(),
                job.getFailCode(),
                job.isDone()
                        ? motifResultRepository.findByJobSeq(job.getJobSeq())
                                .map(MotifResult::getResultSeq)
                                .orElse(null)
                        : null);
    }
}
