package com.ssafy.motif.coreapi.domain.video.service;

import com.ssafy.motif.common.error.CommonErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.domain.video.VideoErrorCode;
import com.ssafy.motif.coreapi.domain.video.dto.VideoItem;
import com.ssafy.motif.coreapi.domain.video.dto.VideoUpdateRequest;
import com.ssafy.motif.domain.core.challenge.ChallengeRepository;
import com.ssafy.motif.domain.core.job.CompositeJob;
import com.ssafy.motif.domain.core.job.CompositeJobRepository;
import com.ssafy.motif.domain.core.video.ParticipationVideo;
import com.ssafy.motif.domain.core.video.ParticipationVideoRepository;
import com.ssafy.motif.domain.core.video.VideoFeedRow;
import com.ssafy.motif.domain.core.video.Visibility;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 참여 영상에 붙는 쓰기 (API 명세 §8.6 · §8.9 · §8.10).
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
    private final CompositeJobRepository compositeJobRepository;
    private final ChallengeRepository challengeRepository;

    /** 수정 응답은 §8.2 와 <b>같은 경로</b>로 읽는다 — 이유는 {@link #update} javadoc 에 있다. */
    private final VideoQueryService videoQueryService;

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

            if (request.visibility() != null && request.visibility() != video.getVisibility()) {
                changeVisibility(video, request.visibility());
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
     * <p><b>산출물은 지우지 않는다.</b> 명세가 소프트 삭제만 적었고, S3 삭제·CloudFront
     * 무효화 수단이 레포에 없다(§8.9 와 같은 상태). 영상 행이 남으므로
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

    /**
     * 공개 범위 전환 — <b>키를 함께 움직인다</b>. 등급을 정하는 것은 {@code visibility}
     * 가 아니라 키 프리픽스이기 때문이다({@code MediaKeys#isProtected}).
     *
     * <p><b>비대칭이다.</b>
     *
     * <ul>
     *   <li><b>{@code PUBLIC → PRIVATE}</b>: 키를 정본({@code private/})으로 되돌린다.
     *       그래야 이후 발급되는 URL 이 보호 경로로 나간다. 정본 위치는 계산하지 않고
     *       {@code composite_jobs} 에서 읽는다 — 워커가 실제로 쓴 자리라 규약이 바뀌어도
     *       어긋나지 않는다({@code MediaKeys#prefixOf} 와 같은 판단).</li>
     *   <li><b>{@code PRIVATE → PUBLIC}</b>: 키를 <b>그대로 둔다</b>. 공개 사본을 만드는
     *       주체가 §8.8 게시인데 아직 없어서, 키만 {@code public/} 으로 바꾸면 그 자리에
     *       객체가 없어 CDN 이 404 를 준다. 보호 경로로 두면 적어도 본인은 재생된다.</li>
     * </ul>
     *
     * <p><b>S3 사본 복사·삭제와 CloudFront 무효화는 하지 않는다</b>(이 티켓 범위 밖).
     * 그래서 {@code PUBLIC → PRIVATE} 이후에도 <b>이미 발급된 무서명 URL 은 살아 있다</b>
     * — 산출물에 1년 {@code immutable} 이 붙어 S3 삭제 없이는 엣지가 계속 서빙하고,
     * 삭제하려면 무효화까지 필요하다(§8.9 표). 지금은 {@code public/} 에 객체를 올리는
     * 주체가 없어 실데이터로는 발생하지 않는다.
     */
    private void changeVisibility(ParticipationVideo video, Visibility to) {
        if (to == Visibility.PRIVATE) {
            CompositeJob job = compositeJobRepository.findById(video.getJobSeq())
                    .orElseThrow(() -> new BusinessException(
                            VideoErrorCode.VIDEO_NOT_FOUND, "jobSeq: " + video.getJobSeq()));
            video.changeVisibility(to, job.getResultVideoKey(), job.getResultThumbnailKey());
            return;
        }
        video.changeVisibility(to, null, null);
    }
}
