package com.ssafy.motif.coreapi.domain.comment.service;

import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.domain.comment.CommentErrorCode;
import com.ssafy.motif.coreapi.domain.comment.CommentTargetType;
import com.ssafy.motif.domain.core.comment.ChallengeCommentRepository;
import com.ssafy.motif.domain.core.comment.NoticeCommentRepository;
import com.ssafy.motif.domain.core.comment.VideoCommentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 댓글 좋아요 토글 (API 명세 §9.5).
 *
 * <p><b>멱등이 애플리케이션 분기가 아니라 DB 제약에서 나온다.</b> "이미 눌렀나" 를 묻고
 * 답에 따라 갈라지면 묻는 시점과 쓰는 시점이 벌어져, 동시에 들어온 두 요청이
 * {@code like_count} 를 두 번 움직인다. 대신 삽입·삭제가 <b>실제로 몇 행을 건드렸는지</b>
 * 를 보고 그때만 카운터를 움직인다 — 복합 PK 가 판정자다
 * ({@code SubscriptionCommandService} §5.6·§5.7 과 같은 방식).
 *
 * <p><b>응답이 없다(204).</b> {@code isLiked}·{@code likeCount} 를 돌려주지 않으므로
 * 클라이언트가 낙관적으로 반영하고 실패 시 롤백한다(§1.2 봉투 예외). 그래서 이 서비스는
 * 아무것도 반환하지 않는다 — 돌려줄 값을 만들려고 카운터를 다시 읽는 왕복이 없다.
 *
 * <p>대상 판정은 읽기·쓰기와 <b>같은 {@link CommentTargetGuard}</b> 를 쓴다. 잠긴 영상은
 * 해제까지 막는다 — 명세 §9.5 가 "생성·해제 모두" 라고 못박았다(§9.4 삭제와 다른 점이다:
 * 그쪽은 이미 쓴 내 글을 내리는 일이라 잠금과 무관하다).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CommentLikeService {

    private final ChallengeCommentRepository challengeCommentRepository;
    private final VideoCommentRepository videoCommentRepository;
    private final NoticeCommentRepository noticeCommentRepository;
    private final CommentTargetGuard targetGuard;

    /** §9.5 좋아요 — 이미 누른 상태면 아무것도 바뀌지 않는다(그래도 204). */
    public void like(Integer userSeq, String type, Long commentSeq) {
        toggle(userSeq, type, commentSeq, true);
    }

    /** §9.5 해제 — 누르지 않은 상태면 아무것도 바뀌지 않는다(그래도 204). */
    public void unlike(Integer userSeq, String type, Long commentSeq) {
        toggle(userSeq, type, commentSeq, false);
    }

    /**
     * <p>생성과 해제가 <b>완전히 같은 판정</b>을 거친다 — 한쪽만 느슨하면 잠긴 영상이나
     * 감춰진 스레드에서 한 방향으로만 카운터가 움직여 되돌릴 수 없는 상태가 남는다.
     */
    private void toggle(Integer userSeq, String type, Long commentSeq, boolean on) {
        CommentTargetType targetType = CommentTargetType.from(type)
                .orElseThrow(() -> commentNotFound(commentSeq));
        int delta = on ? 1 : -1;

        switch (targetType) {
            case CHALLENGE -> {
                Integer challengeSeq = challengeCommentRepository.findVisibleTargetSeq(commentSeq)
                        .orElseThrow(() -> commentNotFound(commentSeq));
                targetGuard.orCommentNotFound(commentSeq,
                        () -> targetGuard.requireChallenge(challengeSeq));

                int changed = on
                        ? challengeCommentRepository.insertLikeIfAbsent(commentSeq, userSeq)
                        : challengeCommentRepository.deleteLikeIfPresent(commentSeq, userSeq);
                if (changed == 1) {
                    challengeCommentRepository.addLikeCount(commentSeq, delta);
                }
            }
            case VIDEO -> {
                Long videoSeq = videoCommentRepository.findVisibleTargetSeq(commentSeq)
                        .orElseThrow(() -> commentNotFound(commentSeq));
                // 잠긴 영상은 해제도 막는다 (§9.5 — §9.4 삭제와 다르다)
                targetGuard.orCommentNotFound(commentSeq,
                        () -> targetGuard.requireVideo(userSeq, videoSeq));

                int changed = on
                        ? videoCommentRepository.insertLikeIfAbsent(commentSeq, userSeq)
                        : videoCommentRepository.deleteLikeIfPresent(commentSeq, userSeq);
                if (changed == 1) {
                    videoCommentRepository.addLikeCount(commentSeq, delta);
                }
            }
            case NOTICE -> {
                Integer noticeSeq = noticeCommentRepository.findVisibleTargetSeq(commentSeq)
                        .orElseThrow(() -> commentNotFound(commentSeq));
                targetGuard.orCommentNotFound(commentSeq,
                        () -> targetGuard.requireNotice(noticeSeq));

                int changed = on
                        ? noticeCommentRepository.insertLikeIfAbsent(commentSeq, userSeq)
                        : noticeCommentRepository.deleteLikeIfPresent(commentSeq, userSeq);
                if (changed == 1) {
                    noticeCommentRepository.addLikeCount(commentSeq, delta);
                }
            }
        }
    }

    private static BusinessException commentNotFound(Long commentSeq) {
        return new BusinessException(CommentErrorCode.COMMENT_NOT_FOUND,
                "commentSeq: " + commentSeq);
    }
}
