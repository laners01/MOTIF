package com.ssafy.motif.coreapi.domain.video.service;

import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.domain.video.VideoErrorCode;
import com.ssafy.motif.coreapi.domain.video.dto.VideoItem;
import com.ssafy.motif.domain.core.video.ParticipationVideoRepository;
import com.ssafy.motif.domain.core.video.VideoFeedRow;
import com.ssafy.motif.domain.core.video.Visibility;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 참여 영상 단건 조회 (API 명세 §8.2).
 *
 * <p><b>이 응답이 "참여 영상 항목" 의 정의다.</b> §6.1 HOT 처럼 경량 항목만 받은 화면이
 * 뷰어에 들어갈 때 여기를 1회 불러 오버레이를 채운다(회의 확정 R2).
 *
 * <p>조회수는 여기서 올리지 않는다 — §8.5 {@code POST /videos/{seq}/view} 가 따로 있고,
 * 읽기만 하는 API 가 쓰기를 겸하면 재시도·프리페치가 그대로 조회수가 된다.
 *
 * <p>읽기 전용 트랜잭션이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VideoQueryService {

    private final ParticipationVideoRepository participationVideoRepository;
    private final VideoItemAssembler assembler;

    /**
     * §8.2 참여 영상 단건.
     *
     * <p><b>실패는 전부 404 한 가지다.</b> 미존재·삭제·삭제 사용자·삭제된 챌린지 소속은
     * 쿼리가 떨어뜨리고, 타인의 {@code PRIVATE} 은 여기서 같은 404 로 만든다 — 403 으로
     * 가르면 "그 영상은 존재하고 당신 것이 아니다" 가 응답에서 읽힌다(§1.8 존재 은닉).
     *
     * <p><b>"관심 없음"(§8.6) 한 영상도 열린다 (기본안).</b> {@code video_hides} 는
     * 피드에서 빼는 장치지 접근 차단이 아니다 — 명세는 §8.1 제외만 적었고, 링크·공유로
     * 직접 들어온 경우까지 막을 근거가 없다.
     *
     * <p>조회 2번이다 — 카드 한 건과 좋아요 여부. 좋아요는 §8.1 의 배치 메서드를 1건으로
     * 부른다. 단건용 {@code exists} 를 따로 만들면 같은 테이블을 읽는 길이 둘이 된다.
     */
    public VideoItem detail(Integer userSeq, Long videoSeq) {
        VideoFeedRow row = participationVideoRepository.findDetailCard(videoSeq)
                .orElseThrow(() -> notFound(videoSeq));

        if (row.visibility() == Visibility.PRIVATE && !row.userSeq().equals(userSeq)) {
            throw notFound(videoSeq);
        }

        boolean liked = !participationVideoRepository
                .findLikedVideoSeqs(userSeq, List.of(videoSeq))
                .isEmpty();

        return assembler.toItem(row, userSeq, liked);
    }

    private static BusinessException notFound(Long videoSeq) {
        return new BusinessException(VideoErrorCode.VIDEO_NOT_FOUND, "videoSeq: " + videoSeq);
    }
}
