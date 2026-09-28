package com.ssafy.motif.coreapi.domain.comment.service;

import com.ssafy.motif.common.error.CommonErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.domain.comment.CommentErrorCode;
import com.ssafy.motif.coreapi.domain.comment.CommentTargetType;
import com.ssafy.motif.coreapi.domain.comment.dto.CommentCreateRequest;
import com.ssafy.motif.coreapi.domain.comment.dto.CommentCreateResponse;
import com.ssafy.motif.coreapi.domain.comment.dto.CommentDeleteResponse;
import com.ssafy.motif.domain.core.challenge.ChallengeRepository;
import com.ssafy.motif.domain.core.comment.ChallengeComment;
import com.ssafy.motif.domain.core.comment.ChallengeCommentRepository;
import com.ssafy.motif.domain.core.comment.NoticeComment;
import com.ssafy.motif.domain.core.comment.NoticeCommentRepository;
import com.ssafy.motif.domain.core.comment.VideoComment;
import com.ssafy.motif.domain.core.comment.VideoCommentRepository;
import com.ssafy.motif.domain.core.notice.NoticeRepository;
import com.ssafy.motif.domain.core.user.UserRepository;
import com.ssafy.motif.domain.core.video.ParticipationVideoRepository;
import com.ssafy.motif.event.contract.CommentCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 댓글·답글 작성과 삭제 (API 명세 §9.3 · §9.4).
 *
 * <p><b>거울 카운터를 같은 트랜잭션에서 움직인다.</b> 댓글 행과 대상의
 * {@code comment_count} 가 따로 놀면 목록은 비어 있는데 "댓글 3" 이라고 쓰인 화면이
 * 남는다 — 되돌릴 방법이 배치밖에 없는 종류의 어긋남이다.
 *
 * <p><b>깊이 2단·부모-대상 일치는 여기서 지킨다.</b> 교차 행 규칙이라 DB CHECK 로
 * 걸 수 없다고 changeSet 0006 이 명시했다.
 *
 * <p>읽기(§9.1·§9.2)는 {@link CommentQueryService} 가 갖는다. 대상 판정은 양쪽 모두
 * {@link CommentTargetGuard} 하나를 쓴다 — 쓰기에서만 보이는 게시물이 생기면 안 된다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CommentCommandService {

    private final ChallengeCommentRepository challengeCommentRepository;
    private final VideoCommentRepository videoCommentRepository;
    private final NoticeCommentRepository noticeCommentRepository;
    private final ChallengeRepository challengeRepository;
    private final ParticipationVideoRepository participationVideoRepository;
    private final NoticeRepository noticeRepository;
    private final CommentTargetGuard targetGuard;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    /** §9.3 챌린지 댓글·답글 작성. */
    public CommentCreateResponse writeOnChallenge(Integer userSeq, Integer challengeSeq,
                                                  CommentCreateRequest request) {
        Integer ownerUserSeq = targetGuard.requireChallenge(challengeSeq);

        Long parentSeq = request.parentCommentSeq();
        Integer recipientUserSeq = ownerUserSeq;
        if (parentSeq != null) {
            ChallengeComment parent = alive(challengeCommentRepository.findById(parentSeq),
                    ChallengeComment::isDeleted, parentSeq);
            recipientUserSeq = parent.getUserSeq();
            requireRoot(parent.getParentCommentSeq());
            requireSameTarget(parent.getChallengeSeq(), challengeSeq);
            requireVisibleThread(challengeCommentRepository.findRootTargetSeq(parentSeq).isPresent(),
                    parentSeq);
        }

        ChallengeComment saved = challengeCommentRepository.save(
                ChallengeComment.write(challengeSeq, userSeq, parentSeq, request.content()));
        challengeRepository.addCommentCount(challengeSeq, 1);
        publishCreated(CommentTargetType.CHALLENGE, challengeSeq.longValue(), saved.getCommentSeq(),
                parentSeq, recipientUserSeq, userSeq, request.content());

        return new CommentCreateResponse(saved.getCommentSeq());
    }

    /**
     * §9.3 참여 영상 댓글·답글 작성 — 잠긴 영상은 403 이다.
     *
     * <p>작성자가 그 영상의 아티스트면 {@code has_artist_comment} 를 올린다(§9.3 처리 5).
     * 그 플래그는 §8.1 피드 정렬에 강하게 반영되므로 댓글 한 건의 문제가 아니다.
     */
    public CommentCreateResponse writeOnVideo(Integer userSeq, Long videoSeq,
                                              CommentCreateRequest request) {
        Integer artistUserSeq = targetGuard.requireVideo(userSeq, videoSeq);

        Long parentSeq = request.parentCommentSeq();
        // 영상의 "게시물 소유자" 는 아티스트가 아니라 올린 사람이다 — artistUserSeq 는
        // has_artist_comment 판정용이라 알림 수신자로 쓰면 엉뚱한 사람에게 간다
        Integer recipientUserSeq = participationVideoRepository.findById(videoSeq)
                .map(video -> video.getUserSeq()).orElse(null);
        if (parentSeq != null) {
            VideoComment parent = alive(videoCommentRepository.findById(parentSeq),
                    VideoComment::isDeleted, parentSeq);
            recipientUserSeq = parent.getUserSeq();
            requireRoot(parent.getParentCommentSeq());
            requireSameTarget(parent.getVideoSeq(), videoSeq);
            requireVisibleThread(videoCommentRepository.findRootTargetSeq(parentSeq).isPresent(),
                    parentSeq);
        }

        VideoComment saved = videoCommentRepository.save(
                VideoComment.write(videoSeq, userSeq, parentSeq, request.content()));
        participationVideoRepository.addCommentCount(videoSeq, 1);
        if (userSeq.equals(artistUserSeq)) {
            participationVideoRepository.updateHasArtistComment(videoSeq, true);
        }
        publishCreated(CommentTargetType.VIDEO, videoSeq, saved.getCommentSeq(),
                parentSeq, recipientUserSeq, userSeq, request.content());

        return new CommentCreateResponse(saved.getCommentSeq());
    }

    /** §9.3 공지 댓글·답글 작성. */
    public CommentCreateResponse writeOnNotice(Integer userSeq, Integer noticeSeq,
                                               CommentCreateRequest request) {
        Integer ownerUserSeq = targetGuard.requireNotice(noticeSeq);

        Long parentSeq = request.parentCommentSeq();
        Integer recipientUserSeq = ownerUserSeq;
        if (parentSeq != null) {
            NoticeComment parent = alive(noticeCommentRepository.findById(parentSeq),
                    NoticeComment::isDeleted, parentSeq);
            recipientUserSeq = parent.getUserSeq();
            requireRoot(parent.getParentCommentSeq());
            requireSameTarget(parent.getNoticeSeq(), noticeSeq);
            requireVisibleThread(noticeCommentRepository.findRootTargetSeq(parentSeq).isPresent(),
                    parentSeq);
        }

        NoticeComment saved = noticeCommentRepository.save(
                NoticeComment.write(noticeSeq, userSeq, parentSeq, request.content()));
        noticeRepository.addCommentCount(noticeSeq, 1);
        publishCreated(CommentTargetType.NOTICE, noticeSeq.longValue(), saved.getCommentSeq(),
                parentSeq, recipientUserSeq, userSeq, request.content());

        return new CommentCreateResponse(saved.getCommentSeq());
    }

    /**
     * §9.4 삭제 — <b>본인 댓글만</b>.
     *
     * <p>네 걸음이다: ① 댓글을 찾고 주인을 확인한다 ② 루트면 <b>살아 있는 직계 답글</b>을
     * 함께 집는다 ③ 그 댓글들의 좋아요를 하드 삭제한다 ④ 소프트 삭제하고 <b>실제로 바뀐
     * 행 수만큼</b> {@code comment_count} 를 깎는다.
     *
     * <p>③이 ④보다 먼저인 것은 좋아요 행이 댓글을 FK 로 참조하기 때문이 아니라(소프트
     * 삭제라 FK 는 그대로다) 순서를 외우기 쉬워서다 — 어느 쪽이든 같은 트랜잭션이다.
     */
    public CommentDeleteResponse delete(Integer userSeq, String type, Long commentSeq) {
        CommentTargetType targetType = CommentTargetType.from(type)
                .orElseThrow(() -> commentNotFound(commentSeq));

        return switch (targetType) {
            case CHALLENGE -> {
                ChallengeComment comment = alive(challengeCommentRepository.findById(commentSeq),
                        ChallengeComment::isDeleted, commentSeq);
                requireMine(comment.getUserSeq(), userSeq);
                Integer challengeSeq = comment.getChallengeSeq();
                targetGuard.orCommentNotFound(commentSeq,
                        () -> targetGuard.requireChallenge(challengeSeq));

                List<Long> targets = withReplies(commentSeq, comment.getParentCommentSeq(),
                        () -> challengeCommentRepository.findAliveReplySeqs(commentSeq));
                challengeCommentRepository.deleteLikes(targets);
                int deleted = challengeCommentRepository.softDelete(targets, userSeq, Instant.now());
                challengeRepository.addCommentCount(challengeSeq, -deleted);

                yield new CommentDeleteResponse(deleted);
            }
            case VIDEO -> {
                VideoComment comment = alive(videoCommentRepository.findById(commentSeq),
                        VideoComment::isDeleted, commentSeq);
                requireMine(comment.getUserSeq(), userSeq);
                Long videoSeq = comment.getVideoSeq();
                // 잠긴 영상에서도 지울 수 있다 — 잠금은 새 글을 막는 장치다(§9.4 실패표)
                Integer artistUserSeq = targetGuard.orCommentNotFound(commentSeq,
                        () -> targetGuard.requireVideoIgnoringCommentLock(userSeq, videoSeq));

                List<Long> targets = withReplies(commentSeq, comment.getParentCommentSeq(),
                        () -> videoCommentRepository.findAliveReplySeqs(commentSeq));
                videoCommentRepository.deleteLikes(targets);
                int deleted = videoCommentRepository.softDelete(targets, userSeq, Instant.now());
                participationVideoRepository.addCommentCount(videoSeq, -deleted);

                // 지운 것이 아티스트 댓글인지 따지지 않고 <b>다시 센다</b> — 루트를 지우면
                // 답글이 함께 지워지고, 그중에 아티스트의 답글이 섞여 있을 수 있다
                participationVideoRepository.updateHasArtistComment(videoSeq,
                        videoCommentRepository.existsArtistComment(videoSeq, artistUserSeq));

                yield new CommentDeleteResponse(deleted);
            }
            case NOTICE -> {
                NoticeComment comment = alive(noticeCommentRepository.findById(commentSeq),
                        NoticeComment::isDeleted, commentSeq);
                requireMine(comment.getUserSeq(), userSeq);
                Integer noticeSeq = comment.getNoticeSeq();
                targetGuard.orCommentNotFound(commentSeq,
                        () -> targetGuard.requireNotice(noticeSeq));

                List<Long> targets = withReplies(commentSeq, comment.getParentCommentSeq(),
                        () -> noticeCommentRepository.findAliveReplySeqs(commentSeq));
                noticeCommentRepository.deleteLikes(targets);
                int deleted = noticeCommentRepository.softDelete(targets, userSeq, Instant.now());
                noticeRepository.addCommentCount(noticeSeq, -deleted);

                yield new CommentDeleteResponse(deleted);
            }
        };
    }

    /**
     * 지울 대상 — 루트면 <b>살아 있는</b> 직계 답글까지, 답글이면 자기 자신만.
     *
     * <p>깊이가 2단이라 답글에는 자식이 없다 — 답글을 지울 때는 조회를 아예 하지 않는다.
     */
    private static List<Long> withReplies(Long commentSeq, Long parentCommentSeq,
                                          Supplier<List<Long>> aliveReplies) {
        if (parentCommentSeq != null) {
            return List.of(commentSeq);
        }
        List<Long> targets = new ArrayList<>(aliveReplies.get());
        targets.add(commentSeq);
        return targets;
    }

    /** 없거나 이미 삭제됐으면 404 — 둘을 가르지 않는다(§9.4 실패표). */
    private static <T> T alive(Optional<T> found, Predicate<T> deleted, Long commentSeq) {
        return found.filter(comment -> !deleted.test(comment))
                .orElseThrow(() -> commentNotFound(commentSeq));
    }

    /**
     * 답글에는 답글을 달 수 없다 (§9.3).
     *
     * <p>부모가 이미 누군가의 답글이면 그 아래는 3단이 된다.
     */
    private static void requireRoot(Long parentOfParent) {
        if (parentOfParent != null) {
            throw new BusinessException(CommentErrorCode.REPLY_DEPTH_EXCEEDED);
        }
    }

    /** 부모가 <b>같은 게시물</b>의 댓글이어야 한다 (§9.3). */
    private static void requireSameTarget(Object parentTargetSeq, Object targetSeq) {
        if (!parentTargetSeq.equals(targetSeq)) {
            throw new BusinessException(CommentErrorCode.PARENT_TARGET_MISMATCH);
        }
    }

    /**
     * 부모가 <b>보이는</b> 루트여야 한다 — 탈퇴 사용자가 쓴 루트에는 답글을 달 수 없다.
     *
     * <p>그 스레드는 §9.1·§9.2 에서 통째로 감춰져 있다(§1.8). 달 수 있게 두면 아무도
     * 볼 수 없는 답글이 쌓이고, 대상의 {@code comment_count} 만 올라간다.
     */
    private static void requireVisibleThread(boolean visible, Long parentSeq) {
        if (!visible) {
            throw commentNotFound(parentSeq);
        }
    }

    /** 남의 댓글은 지울 수 없다 (§9.4 — 403). */
    /** 푸시 본문에 들어가는 앞자락 — 공지 알림과 같은 60자다(§9.3 · 어드민 명세 §4). */
    private static final int EVENT_PREVIEW_LENGTH = 60;

    /**
     * 댓글 알림 이벤트 (§9.3 · ADMIN-11). MQ 로 나가는 시점은 커밋 후다
     * ({@link com.ssafy.motif.coreapi.domain.comment.CommentEventRelay}).
     *
     * <p><b>본인에게는 발행하지 않는다.</b> 자기 게시물에 자기가 댓글을 달거나 자기
     * 댓글에 자기가 답글을 달면 알릴 것이 없다 — 대행에서 아티스트가 자기 공지에
     * 댓글을 다는 경우가 여기 걸린다(ADMIN-11 자기 행위 규칙).
     *
     * <p>수신자를 못 찾으면(삭제된 영상 등) 조용히 건너뛴다 — 댓글 자체는 이미 규칙을
     * 통과했으므로 알림 때문에 되돌리지 않는다.
     */
    private void publishCreated(CommentTargetType targetType, Long targetSeq, Long commentSeq,
                                Long parentCommentSeq, Integer recipientUserSeq,
                                Integer actorUserSeq, String content) {
        if (recipientUserSeq == null || recipientUserSeq.equals(actorUserSeq)) {
            return;
        }
        String actorNickname = userRepository.findById(actorUserSeq)
                .map(user -> user.getNickname()).orElse(null);

        eventPublisher.publishEvent(new CommentCreatedEvent(
                UUID.randomUUID().toString(), Instant.now(),
                targetType.name(), targetSeq, commentSeq, parentCommentSeq,
                recipientUserSeq, actorNickname, preview(content)));
    }

    private static String preview(String content) {
        return content.length() <= EVENT_PREVIEW_LENGTH
                ? content : content.substring(0, EVENT_PREVIEW_LENGTH);
    }

    private static void requireMine(Integer ownerSeq, Integer userSeq) {
        if (!ownerSeq.equals(userSeq)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "userSeq: " + userSeq);
        }
    }

    private static BusinessException commentNotFound(Long commentSeq) {
        return new BusinessException(CommentErrorCode.COMMENT_NOT_FOUND,
                "commentSeq: " + commentSeq);
    }
}
