package com.ssafy.motif.coreapi.domain.video.service;

import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.domain.video.VideoErrorCode;
import com.ssafy.motif.domain.core.video.ParticipationVideoRepository;
import com.ssafy.motif.domain.core.video.VideoFeedRow;
import com.ssafy.motif.domain.core.video.Visibility;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * "이 영상이 이 사용자에게 보이는가" — <b>§8 전체가 공유하는 단일 판정</b>
 * (API 명세 §8.2 실패표).
 *
 * <p>§8.2 단건뿐 아니라 §8.3 좋아요 · §8.5 조회 · §8.6 관심 없음 · §8.7 신고가 모두
 * 같은 질문으로 시작한다. 판정을 각자 두면 <b>한쪽에서만 보이는 영상</b>이 생긴다 —
 * 예를 들어 타인의 {@code PRIVATE} 을 단건 조회는 404 로 막는데 신고는 받아 주면,
 * 신고 응답이 "그 영상은 존재한다"를 알려 주는 탐지 수단이 된다(§1.8 존재 은닉이
 * 뚫리는 방식이 대개 이렇다).
 *
 * <p>안 보이는 이유는 다섯 가지지만 <b>답은 하나</b>다 — 미존재 · 삭제 영상 · 삭제
 * 사용자의 콘텐츠 · 삭제된 챌린지 소속은 쿼리가 떨어뜨리고, 타인의 {@code PRIVATE} 은
 * 여기서 같은 404 로 만든다.
 */
@Component
@RequiredArgsConstructor
public class VideoAccessGuard {

    private final ParticipationVideoRepository participationVideoRepository;

    /**
     * 보이면 그 행을, 안 보이면 404 를 준다.
     *
     * <p>행을 함께 돌려주는 것은 호출부가 대개 그 값을 쓰기 때문이다(§8.2 는 응답을
     * 조립하고, §8.7 은 {@code videoSeq} 를 그대로 신고에 넣는다). 존재 확인만 하는
     * 가벼운 메서드를 따로 두면 판정이 둘로 갈린다.
     */
    public VideoFeedRow requireVisible(Integer userSeq, Long videoSeq) {
        VideoFeedRow row = participationVideoRepository.findDetailCard(videoSeq)
                .orElseThrow(() -> notFound(videoSeq));

        if (row.visibility() == Visibility.PRIVATE && !row.userSeq().equals(userSeq)) {
            throw notFound(videoSeq);
        }
        return row;
    }

    private static BusinessException notFound(Long videoSeq) {
        return new BusinessException(VideoErrorCode.VIDEO_NOT_FOUND, "videoSeq: " + videoSeq);
    }
}
