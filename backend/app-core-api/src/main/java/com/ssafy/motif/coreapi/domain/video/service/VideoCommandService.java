package com.ssafy.motif.coreapi.domain.video.service;

import com.ssafy.motif.domain.core.video.ParticipationVideoRepository;
import com.ssafy.motif.domain.core.video.VideoFeedRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 참여 영상에 붙는 작은 쓰기 (API 명세 §8.6).
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
}
