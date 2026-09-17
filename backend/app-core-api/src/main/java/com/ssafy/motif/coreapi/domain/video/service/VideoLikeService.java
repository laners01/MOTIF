package com.ssafy.motif.coreapi.domain.video.service;

import com.ssafy.motif.domain.core.video.ParticipationVideoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 참여 영상 좋아요 토글 (API 명세 §8.3).
 *
 * <p><b>멱등이 애플리케이션 분기가 아니라 DB 제약에서 나온다.</b> "이미 눌렀나" 를 묻고
 * 답에 따라 갈라지면 묻는 시점과 쓰는 시점이 벌어져, 동시에 들어온 두 요청이
 * {@code participation_videos.like_count} 를 두 번 움직인다. 대신 삽입·삭제가 <b>실제로
 * 몇 행을 건드렸는지</b>를 보고 그때만 카운터를 움직인다 — {@code video_likes} 의 복합
 * PK 가 판정자다(§9.5 {@code CommentLikeService} · §10.2 {@code NoticeLikeService} 와
 * 같은 방식).
 *
 * <p><b>응답이 없다(204).</b> {@code isLiked}·{@code likeCount} 를 돌려주지 않으므로
 * 클라이언트가 낙관적으로 반영하고 실패 시 롤백한다(§1.2 봉투 예외). 정확한 값이
 * 필요하면 §8.1·§8.2 를 다시 받는다.
 *
 * <p><b>가시성 판정은 {@link VideoAccessGuard} 를 쓴다.</b> §8.2 단건·§8.7 신고와 같은
 * 질문이라 그 javadoc 이 §8.3 을 공유자로 이미 적어 두었다 — 여기만 느슨하면 좋아요
 * 응답이 타인의 {@code PRIVATE} 영상 존재를 알려 주는 탐지 수단이 된다(§1.8).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class VideoLikeService {

    private final VideoAccessGuard accessGuard;
    private final ParticipationVideoRepository participationVideoRepository;

    /** §8.3 좋아요 — 이미 누른 상태면 아무것도 바뀌지 않는다(그래도 204). */
    public void like(Integer userSeq, Long videoSeq) {
        toggle(userSeq, videoSeq, true);
    }

    /** §8.3 해제 — 누르지 않은 상태면 아무것도 바뀌지 않는다(그래도 204). */
    public void unlike(Integer userSeq, Long videoSeq) {
        toggle(userSeq, videoSeq, false);
    }

    /**
     * 생성과 해제가 <b>완전히 같은 판정</b>을 거친다 — 한쪽만 느슨하면 안 보이는 영상에서
     * 한 방향으로만 카운터가 움직여 되돌릴 수 없는 상태가 남는다(§10.2 와 같은 근거).
     */
    private void toggle(Integer userSeq, Long videoSeq, boolean on) {
        accessGuard.requireVisible(userSeq, videoSeq);

        int changed = on
                ? participationVideoRepository.insertLikeIfAbsent(videoSeq, userSeq)
                : participationVideoRepository.deleteLikeIfPresent(videoSeq, userSeq);
        if (changed != 1) {
            return;
        }
        participationVideoRepository.addLikeCount(videoSeq, on ? 1 : -1);

        // §8.3 "아티스트 계정이 좋아요를 누르면 서비스가 has_artist_like 를 재계산한다".
        // 영상에 걸린 아티스트는 하나뿐이라 그 사람의 토글이 곧 플래그다 — 댓글처럼
        // 남은 것을 세어 볼 필요가 없다(updateHasArtistLike javadoc)
        if (isVideoArtist(userSeq, videoSeq)) {
            participationVideoRepository.updateHasArtistLike(videoSeq, on);
        }
    }

    private boolean isVideoArtist(Integer userSeq, Long videoSeq) {
        return participationVideoRepository.findArtistUserSeq(videoSeq)
                .map(userSeq::equals)
                .orElse(false);
    }
}
