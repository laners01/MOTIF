package com.ssafy.motif.coreapi.domain.video.service;

import com.ssafy.motif.common.error.CommonErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.domain.composite.CompositeErrorCode;
import com.ssafy.motif.coreapi.domain.video.VideoErrorCode;
import com.ssafy.motif.coreapi.domain.video.dto.VideoItem;
import com.ssafy.motif.coreapi.domain.video.dto.VideoPublishRequest;
import com.ssafy.motif.coreapi.domain.video.dto.VideoPublishResponse;
import com.ssafy.motif.coreapi.domain.video.dto.VideoUpdateRequest;
import com.ssafy.motif.coreapi.global.security.AuthenticatedUser;
import com.ssafy.motif.domain.core.challenge.Challenge;
import com.ssafy.motif.domain.core.challenge.ChallengeRepository;
import com.ssafy.motif.domain.core.job.CompositeJob;
import com.ssafy.motif.domain.core.job.CompositeJobRepository;
import com.ssafy.motif.domain.core.job.JobType;
import com.ssafy.motif.domain.core.user.UserRole;
import com.ssafy.motif.domain.core.video.ParticipationVideo;
import com.ssafy.motif.domain.core.video.ParticipationVideoRepository;
import com.ssafy.motif.domain.core.video.VideoFeedRow;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 참여 영상에 붙는 쓰기 (API 명세 §8.6 · §8.8 · §8.9 · §8.10).
 *
 * <p><b>{@link VideoQueryService} 와 나란한 별개 서비스다.</b> 그쪽은
 * {@code @Transactional(readOnly = true)} 로 선언돼 있어 쓰기를 한 줄이라도 섞으면 그
 * 표시가 거짓말이 된다({@code SubscriptionCommandService} 가 갈라진 것과 같은 근거).
 *
 * <p><b>{@link VideoReportService} 와도 나누어 둔다.</b> 신고는 {@code reports} 를
 * 건드리고 §9.6 댓글 신고와 규칙(1인 1대상 1회·처리 상태)을 공유한다 — 바뀌는 이유가
 * 다르다. 여기는 참여 영상에 딸린 부수 테이블을 만지는 자리이고, §8.3 좋아요·§8.5
 * 조회가 이어 붙을 곳이다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class VideoCommandService {

    private final VideoAccessGuard accessGuard;
    private final ParticipationVideoRepository participationVideoRepository;
    private final ChallengeRepository challengeRepository;
    private final CompositeJobRepository compositeJobRepository;

    /** 수정 응답은 §8.2 와 <b>같은 경로</b>로 읽는다 — 이유는 {@link #update} javadoc 에 있다. */
    private final VideoQueryService videoQueryService;

    /**
     * §8.8 게시 — 합성이 끝난 잡을 참여 영상으로 만든다. <b>201 Created</b>.
     *
     * <p><b>바이트를 옮기지 않는다.</b> 산출물은 이미 {@code private/challenge/{jobUuid}/}
     * 에 있고, 게시는 그것을 가리키는 행을 만들고 {@code visibility} 를 정하는 일까지다
     * (ADR-0013). <b>공개 사본 트리도 복사기도 없다</b> — {@code visibility} 는 앱 안의
     * 공개 범위이고 전송 등급은 서명이 맡는다.
     *
     * <p>판정 순서는 명세의 서버 처리 순서 그대로다.
     *
     * <ol>
     *   <li>{@code ARTIST} 면 403 — 아티스트 계정은 참여 영상을 게시할 수 없다</li>
     *   <li>본인 {@code CHALLENGE}·{@code DONE} 잡인가</li>
     *   <li>원본 챌린지가 살아 있는가 — 가공 중에 어드민이 지웠을 수 있다</li>
     *   <li>행 생성 + {@code participant_count + 1}</li>
     * </ol>
     *
     * <p><b>중복 게시는 유니크 제약이 잡는다.</b> 미리 조회해 막으면 동시 요청 둘이 모두
     * 통과한 뒤 두 번째가 제약 위반 500 이 된다 — {@code uq_participation_videos_job} 을
     * 판정 근거로 삼고 충돌을 409 로 번역한다.
     *
     * @throws BusinessException 403 {@code FORBIDDEN} · 404 {@code JOB_NOT_FOUND} ·
     *                           409 {@code JOB_NOT_DONE} / {@code ALREADY_PUBLISHED} /
     *                           {@code CHALLENGE_DELETED}
     */
    public VideoPublishResponse publish(AuthenticatedUser user, VideoPublishRequest request) {
        if (user.role() == UserRole.ARTIST) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN,
                    "아티스트 계정은 참여 영상을 게시할 수 없다: userSeq=" + user.userSeq());
        }
        CompositeJob job = compositeJobRepository.findByJobUuid(request.jobUuid())
                .filter(candidate -> candidate.isOwnedBy(user.userSeq()))
                .filter(candidate -> candidate.getJobType() == JobType.CHALLENGE)
                .orElseThrow(() -> new BusinessException(
                        CompositeErrorCode.JOB_NOT_FOUND, "jobUuid: " + request.jobUuid()));
        if (!job.isDone()) {
            throw new BusinessException(VideoErrorCode.JOB_NOT_DONE, "status: " + job.getStatus());
        }
        // 가공이 도는 사이 어드민이 지웠을 수 있다 — 189 의 packaged 가 보는 것과 같은 조건
        Challenge challenge = challengeRepository
                .findByChallengeSeqAndDeletedFalse(job.getChallengeSeq())
                .orElseThrow(() -> new BusinessException(VideoErrorCode.CHALLENGE_DELETED,
                        "challengeSeq: " + job.getChallengeSeq()));

        ParticipationVideo video = ParticipationVideo.publish(
                challenge.getChallengeSeq(),
                // 잡에는 없는 값이다 — 게시자는 팬이고 아티스트는 챌린지가 갖는다
                challenge.getArtistSeq(),
                user.userSeq(),
                job.getJobSeq(),
                request.description(),
                // 잡 결과를 그대로 가리킨다 — 키를 만들지도 옮기지도 않는다
                job.getResultVideoKey(),
                job.getResultThumbnailKey(),
                // 잡에 길이 칸이 없다(기본안)
                challenge.getDurationMs(),
                request.visibility(),
                request.commentEnabled());
        try {
            participationVideoRepository.saveAndFlush(video);
        } catch (DataIntegrityViolationException e) {
            // 제약 이름을 확인하고 번역한다. 무엇이든 ALREADY_PUBLISHED 로 접으면 NOT NULL·
            // CHECK 위반 같은 우리 버그가 "이미 게시됨" 으로 위장한다 — 실제로 한 번 겪었다
            if (!isDuplicateJob(e)) {
                throw e;
            }
            throw new BusinessException(VideoErrorCode.ALREADY_PUBLISHED,
                    "jobUuid: " + request.jobUuid());
        }
        challengeRepository.incrementParticipantCount(challenge.getChallengeSeq());

        return new VideoPublishResponse(video.getVideoSeq());
    }

    /**
     * {@code uq_participation_videos_job} 위반인가 — 다른 제약이면 감추지 않고 올린다.
     *
     * <p><b>원인 체인을 훑는다.</b> {@code getMostSpecificCause()} 는 <b>최하위</b>
     * ({@code PSQLException})를 주는데 제약 이름을 들고 있는 것은 그 위의 Hibernate
     * {@link ConstraintViolationException} 이다.
     */
    private static boolean isDuplicateJob(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return "uq_participation_videos_job".equalsIgnoreCase(violation.getConstraintName());
            }
        }
        return false;
    }

    /**
     * §8.6 관심 없음 — 이후 §8.1 피드에서 뺀다.
     *
     * <p><b>멱등이다.</b> 이미 숨긴 영상을 다시 눌러도 200 이고, "이미 숨김" 을 알려주는
     * 코드도 없다 — 사용자가 원하는 상태가 같은데 두 번째만 실패로 보이면 클라이언트는
     * 되돌릴 방법도 없이 오류를 그려야 한다(해제 API 가 없다).
     *
     * <p><b>보이지 않는 영상은 숨길 대상도 아니다.</b> 판정을 §8.2·§8.7 과 공유하는
     * 이유는 {@link VideoAccessGuard} javadoc 에 있다 — 여기만 느슨하면 응답이 타인의
     * {@code PRIVATE} 영상의 존재를 알려 주는 탐지 수단이 된다.
     *
     * <p><b>숨김은 접근 차단이 아니다.</b> §8.2 단건 조회는 그대로 열린다(그쪽 기본안) —
     * 링크로 직접 들어오는 길까지 막을 근거가 명세에 없다.
     */
    public void hide(Integer userSeq, Long videoSeq) {
        VideoFeedRow video = accessGuard.requireVisible(userSeq, videoSeq);

        participationVideoRepository.insertHideIfAbsent(userSeq, video.videoSeq());
    }

    /**
     * §8.9 게시 정보 수정 — 보낸 필드만 반영한다.
     *
     * <p>권한 규칙은 {@link #requireMine} 이 갖는다 — 404 가 403 보다 앞선다.
     *
     * <p><b>빈 요청은 200 이다.</b> 아무것도 바꾸지 않고 현재 상태를 돌려준다 —
     * "보낸 필드만 반영" 의 자연스러운 극단이고, 이걸 막을 코드가 §15 표에 없다.
     *
     * <p><b>등급 전환도 값 하나다.</b> 산출물은 게시 여부와 무관하게 {@code private/}
     * 한 곳에 있으므로 키를 옮기지 않고 S3 · CloudFront 도 건드리지 않는다(ADR-0013).
     * {@code PUBLIC → PRIVATE} 은 서명 갱신 인가가 닫는다 —
     * {@code MediaPlaylistQueryService} 가 갱신 때마다 이 값을 다시 보므로 이미 발급된
     * 서명이 만료될 때까지(기본 10분)가 최대 지연이다.
     *
     * <p>응답은 {@link VideoQueryService#detail} 을 그대로 부른다. 수정 반영본을 손으로
     * 조립하면 §8.2 와 미묘하게 다른 항목이 나갈 수 있는데, 명세는 둘이 <b>같은 형태</b>
     * 라고 못박았다. 조회 한 번을 더 쓰더라도 같은 경로로 읽는다(JPQL 이 돌기 전에
     * 변경이 flush 되므로 수정 반영본이 나온다).
     */
    public VideoItem update(Integer userSeq, Long videoSeq, VideoUpdateRequest request) {
        requireMine(userSeq, videoSeq);

        if (!request.isEmpty()) {
            ParticipationVideo video = participationVideoRepository.findById(videoSeq)
                    .orElseThrow(() -> new BusinessException(
                            VideoErrorCode.VIDEO_NOT_FOUND, "videoSeq: " + videoSeq));

            video.edit(request.description(), request.commentEnabled(), userSeq);

            if (request.visibility() != null) {
                video.changeVisibility(request.visibility());
            }
        }

        return videoQueryService.detail(userSeq, videoSeq);
    }

    /**
     * §8.10 게시 영상 삭제 — <b>소프트 삭제</b>.
     *
     * <p>권한 규칙은 §8.9 와 같다 — 404 가 403 보다 앞선다({@link #update} javadoc).
     *
     * <p><b>이미 삭제된 영상은 404 다</b>(멱등 200 이 아니다). {@link VideoAccessGuard}
     * 가 삭제분을 404 로 만드는데 여기서만 예외를 두면 "없는 영상" 과 "이미 지운 영상" 이
     * 다른 코드로 갈린다 — §1.8 은 그 둘을 구분하지 않는다.
     *
     * <p>그래서 {@code softDelete} 가 0행을 돌려주는 경우는 <b>동시 요청</b>뿐이고,
     * 그때 참여 수를 내리지 않는 것이 이 분기의 전부다. 두 요청이 모두 카운터를 내리면
     * {@code ck_challenges_participant_count} 가 막아 두 번째가 500 이 된다.
     *
     * <p><b>산출물은 지우지 않는다.</b> 명세가 소프트 삭제만 적었고, 지워야 닫히는 것도
     * 아니다 — 산출물이 전부 보호 등급이라 재생에 서명이 필요한데
     * {@code MediaPlaylistQueryService} 가 삭제분에는 갱신을 내주지 않는다. 서명 TTL
     * 안에 닫힌다(§8.9 와 같은 근거 — ADR-0013). 영상 행이 남으므로
     * {@code challengeSeq} 도 그대로 읽을 수 있다.
     *
     * <p><b>어드민 신고 인용(§3.13.3)과 조합을 공유하지 않는다.</b> 그쪽은 응답에 카운터
     * 변화량을 실어야 해서 반환이 다르다. 멱등의 핵심인 "0행이면 카운터를 안 내린다" 는
     * {@code softDelete} 가 이미 강제한다.
     */
    public void delete(Integer userSeq, Long videoSeq) {
        VideoFeedRow row = requireMine(userSeq, videoSeq);

        if (participationVideoRepository.softDelete(videoSeq, userSeq) == 1) {
            challengeRepository.decrementParticipantCount(row.challengeSeq());
        }
    }

    /**
     * 보이는 영상이면서 내 것인가 — §8.9·§8.10 이 함께 쓴다.
     *
     * <p><b>404 가 403 보다 앞선다.</b> 남의 {@code PRIVATE} 영상은 403 이 아니라 404 다
     * — 403 은 "그 영상이 존재한다" 를 알려 준다(§1.8 존재 은닉). 403 은 <b>보이는데 내
     * 것이 아닐 때</b>, 즉 남의 {@code PUBLIC} 영상에만 나간다.
     */
    private VideoFeedRow requireMine(Integer userSeq, Long videoSeq) {
        VideoFeedRow row = accessGuard.requireVisible(userSeq, videoSeq);
        if (!row.userSeq().equals(userSeq)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "videoSeq: " + videoSeq);
        }
        return row;
    }

}
