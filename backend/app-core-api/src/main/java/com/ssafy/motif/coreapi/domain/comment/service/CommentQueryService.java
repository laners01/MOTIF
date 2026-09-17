package com.ssafy.motif.coreapi.domain.comment.service;

import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.common.response.PageResponse;
import com.ssafy.motif.coreapi.domain.comment.CommentErrorCode;
import com.ssafy.motif.coreapi.domain.comment.CommentTargetType;
import com.ssafy.motif.coreapi.domain.comment.dto.CommentItem;
import com.ssafy.motif.coreapi.domain.comment.dto.ReplyItem;
import com.ssafy.motif.coreapi.global.media.MediaUrlResolver;
import com.ssafy.motif.coreapi.global.response.PageResponses;
import com.ssafy.motif.domain.core.comment.ChallengeCommentRepository;
import com.ssafy.motif.domain.core.comment.CommentReplyCount;
import com.ssafy.motif.domain.core.comment.CommentRow;
import com.ssafy.motif.domain.core.comment.NoticeCommentRepository;
import com.ssafy.motif.domain.core.comment.VideoCommentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 루트 댓글 목록 (API 명세 §9.1) — 챌린지·참여 영상·공지.
 *
 * <p><b>대상마다 메서드가 하나씩이다.</b> 경로가 대상 리소스 하위로 갈리고(§9.1) 테이블도
 * 셋이라 쿼리는 어차피 셋인데, 여기서 {@code switch} 로 묶으면 대상 타입이 서비스 안쪽까지
 * 흘러 들어간다. <b>대상별로 다른 것은 세 줄뿐</b>이고(판정 · 어느 리포지토리를 부르는가)
 * 나머지 조립은 {@link #assemble} 하나가 한다.
 *
 * <p>정렬·비노출은 쿼리가 건다 — 이 클래스는 정렬을 다시 하지 않는다. 페이지를 읽어 와
 * 여기서 정렬하면 "전체를 정렬한 뒤 자른다" 는 §9.1 계약이 깨진다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentQueryService {

    private final ChallengeCommentRepository challengeCommentRepository;
    private final VideoCommentRepository videoCommentRepository;
    private final NoticeCommentRepository noticeCommentRepository;
    private final CommentTargetGuard targetGuard;
    private final MediaUrlResolver mediaUrlResolver;

    /** §9.1 챌린지 댓글. */
    public PageResponse<CommentItem> challengeComments(Integer viewerSeq, Integer challengeSeq,
                                                       int page, int size) {
        Integer artistUserSeq = targetGuard.requireChallenge(challengeSeq);
        Page<CommentRow> rows = challengeCommentRepository.findRootPage(
                challengeSeq, artistUserSeq, viewerSeq, pageable(page, size));

        return assemble(rows, viewerSeq, artistUserSeq,
                seqs -> challengeCommentRepository.findLikedCommentSeqs(viewerSeq, seqs),
                challengeCommentRepository::countRepliesByParents);
    }

    /** §9.1 참여 영상 댓글 — 잠긴 영상은 목록도 403 이다. */
    public PageResponse<CommentItem> videoComments(Integer viewerSeq, Long videoSeq,
                                                   int page, int size) {
        Integer artistUserSeq = targetGuard.requireVideo(viewerSeq, videoSeq);
        Page<CommentRow> rows = videoCommentRepository.findRootPage(
                videoSeq, artistUserSeq, viewerSeq, pageable(page, size));

        return assemble(rows, viewerSeq, artistUserSeq,
                seqs -> videoCommentRepository.findLikedCommentSeqs(viewerSeq, seqs),
                videoCommentRepository::countRepliesByParents);
    }

    /** §9.1 공지 댓글. */
    public PageResponse<CommentItem> noticeComments(Integer viewerSeq, Integer noticeSeq,
                                                    int page, int size) {
        Integer artistUserSeq = targetGuard.requireNotice(noticeSeq);
        Page<CommentRow> rows = noticeCommentRepository.findRootPage(
                noticeSeq, artistUserSeq, viewerSeq, pageable(page, size));

        return assemble(rows, viewerSeq, artistUserSeq,
                seqs -> noticeCommentRepository.findLikedCommentSeqs(viewerSeq, seqs),
                noticeCommentRepository::countRepliesByParents);
    }

    /**
     * §9.2 답글 펼치기 — <b>등록순 고정</b>.
     *
     * <p>세 단계다: ① {@code type} 을 푼다 ② 그 테이블에서 <b>루트</b> 댓글을 찾아 대상
     * seq 를 얻는다 ③ 그 대상에 §9.1 과 <b>같은 접근 판정</b>을 건다. ②에서 미존재 ·
     * 삭제 · 루트 아님 · 탈퇴 작성자가 한꺼번에 떨어지고(쿼리가 건다), ③에서 삭제된
     * 챌린지 · 타인의 {@code PRIVATE} · 잠긴 영상이 걸린다.
     */
    public PageResponse<ReplyItem> replies(Integer viewerSeq, String type, Long commentSeq,
                                           int page, int size) {
        CommentTargetType targetType = CommentTargetType.from(type)
                .orElseThrow(() -> commentNotFound(commentSeq));

        return switch (targetType) {
            case CHALLENGE -> {
                Integer challengeSeq = challengeCommentRepository.findRootTargetSeq(commentSeq)
                        .orElseThrow(() -> commentNotFound(commentSeq));
                Integer artistUserSeq = visibleTarget(commentSeq,
                        () -> targetGuard.requireChallenge(challengeSeq));
                yield assembleReplies(
                        challengeCommentRepository.findReplyPage(commentSeq, pageable(page, size)),
                        viewerSeq, artistUserSeq,
                        seqs -> challengeCommentRepository.findLikedCommentSeqs(viewerSeq, seqs));
            }
            case VIDEO -> {
                Long videoSeq = videoCommentRepository.findRootTargetSeq(commentSeq)
                        .orElseThrow(() -> commentNotFound(commentSeq));
                Integer artistUserSeq = visibleTarget(commentSeq,
                        () -> targetGuard.requireVideo(viewerSeq, videoSeq));
                yield assembleReplies(
                        videoCommentRepository.findReplyPage(commentSeq, pageable(page, size)),
                        viewerSeq, artistUserSeq,
                        seqs -> videoCommentRepository.findLikedCommentSeqs(viewerSeq, seqs));
            }
            case NOTICE -> {
                Integer noticeSeq = noticeCommentRepository.findRootTargetSeq(commentSeq)
                        .orElseThrow(() -> commentNotFound(commentSeq));
                Integer artistUserSeq = visibleTarget(commentSeq,
                        () -> targetGuard.requireNotice(noticeSeq));
                yield assembleReplies(
                        noticeCommentRepository.findReplyPage(commentSeq, pageable(page, size)),
                        viewerSeq, artistUserSeq,
                        seqs -> noticeCommentRepository.findLikedCommentSeqs(viewerSeq, seqs));
            }
        };
    }

    /**
     * 대상 접근 판정을 걸되 안 보이는 대상은 "댓글이 없다" 로 바꾼다 —
     * 근거는 {@link CommentTargetGuard#orCommentNotFound} 에 있다.
     */
    private Integer visibleTarget(Long commentSeq, Supplier<Integer> guard) {
        return targetGuard.orCommentNotFound(commentSeq, guard);
    }

    private static BusinessException commentNotFound(Long commentSeq) {
        return new BusinessException(CommentErrorCode.COMMENT_NOT_FOUND,
                "commentSeq: " + commentSeq);
    }

    /** §1.5 상한(100)과 음수 방어는 {@code PageResponses} 가 갖는다 — 여기서 자르지 않는다. */
    private static Pageable pageable(int page, int size) {
        return PageResponses.pageable(page, size);
    }

    /**
     * 한 페이지를 응답 항목으로 — <b>{@code isLiked}·{@code replyCount} 는 두 번의 질의로
     * 끝낸다</b>.
     *
     * <p>항목마다 물으면 한 페이지(최대 100건)에 왕복이 200번이다. 페이지의 seq 를 통째로
     * 넘겨 <b>내가 누른 것만</b> · <b>답글이 있는 것만</b> 받아 대조한다
     * ({@code NoticeRepository.findLikedNoticeSeqs} 와 같은 방식).
     *
     * <p>빈 페이지면 질의를 아예 하지 않는다 — {@code in ()} 은 SQL 이 아니다.
     */
    private PageResponse<CommentItem> assemble(Page<CommentRow> rows, Integer viewerSeq,
                                               Integer artistUserSeq,
                                               Function<List<Long>, List<Long>> likedFinder,
                                               Function<List<Long>, List<CommentReplyCount>> replyCounter) {
        List<Long> commentSeqs = rows.getContent().stream().map(CommentRow::commentSeq).toList();

        Set<Long> liked = commentSeqs.isEmpty()
                ? Set.of()
                : Set.copyOf(likedFinder.apply(commentSeqs));
        Map<Long, Long> replyCounts = commentSeqs.isEmpty()
                ? Map.of()
                : replyCounter.apply(commentSeqs).stream()
                        .collect(Collectors.toMap(CommentReplyCount::commentSeq,
                                CommentReplyCount::count));

        return PageResponses.of(rows,
                row -> toItem(row, viewerSeq, artistUserSeq, liked, replyCounts));
    }

    /**
     * 답글 한 페이지 — {@link #assemble} 과 달리 답글 수를 세지 않는다.
     *
     * <p>답글에는 답글이 없으므로(깊이 2단 고정) 질의가 하나 적다.
     */
    private PageResponse<ReplyItem> assembleReplies(Page<CommentRow> rows, Integer viewerSeq,
                                                    Integer artistUserSeq,
                                                    Function<List<Long>, List<Long>> likedFinder) {
        List<Long> commentSeqs = rows.getContent().stream().map(CommentRow::commentSeq).toList();
        Set<Long> liked = commentSeqs.isEmpty()
                ? Set.of()
                : Set.copyOf(likedFinder.apply(commentSeqs));

        return PageResponses.of(rows, row -> new ReplyItem(
                row.commentSeq(),
                row.content(),
                author(row, artistUserSeq),
                row.likeCount(),
                liked.contains(row.commentSeq()),
                row.authorUserSeq().equals(viewerSeq),
                row.regDttm()));
    }

    private CommentItem toItem(CommentRow row, Integer viewerSeq, Integer artistUserSeq,
                               Set<Long> liked, Map<Long, Long> replyCounts) {
        return new CommentItem(
                row.commentSeq(),
                row.content(),
                author(row, artistUserSeq),
                row.likeCount(),
                liked.contains(row.commentSeq()),
                replyCounts.getOrDefault(row.commentSeq(), 0L),
                row.authorUserSeq().equals(viewerSeq),
                row.regDttm());
    }

    /**
     * 작성자 요약 — §9.1 항목과 §9.2 답글이 <b>같은 조립</b>을 쓴다.
     *
     * <p>{@code isArtist} 판정이 갈리면 같은 사람이 루트에서는 뱃지를 달고 답글에서는
     * 안 다는 화면이 나온다.
     */
    private CommentItem.Author author(CommentRow row, Integer artistUserSeq) {
        return new CommentItem.Author(
                row.authorUserSeq(),
                row.authorNickname(),
                mediaUrlResolver.resolve(row.authorProfileImageKey()),
                row.authorUserSeq().equals(artistUserSeq));
    }
}
