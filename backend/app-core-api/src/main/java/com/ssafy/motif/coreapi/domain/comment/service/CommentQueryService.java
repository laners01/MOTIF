package com.ssafy.motif.coreapi.domain.comment.service;

import com.ssafy.motif.common.response.PageResponse;
import com.ssafy.motif.coreapi.domain.comment.dto.CommentItem;
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

    private CommentItem toItem(CommentRow row, Integer viewerSeq, Integer artistUserSeq,
                               Set<Long> liked, Map<Long, Long> replyCounts) {
        return new CommentItem(
                row.commentSeq(),
                row.content(),
                new CommentItem.Author(
                        row.authorUserSeq(),
                        row.authorNickname(),
                        mediaUrlResolver.resolve(row.authorProfileImageKey()),
                        row.authorUserSeq().equals(artistUserSeq)),
                row.likeCount(),
                liked.contains(row.commentSeq()),
                replyCounts.getOrDefault(row.commentSeq(), 0L),
                row.authorUserSeq().equals(viewerSeq),
                row.regDttm());
    }
}
