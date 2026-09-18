package com.ssafy.motif.coreapi.domain.user.service;

import com.ssafy.motif.common.response.CursorResponse;
import com.ssafy.motif.coreapi.domain.user.dto.ActivityItem;
import com.ssafy.motif.coreapi.global.media.MediaUrlResolver;
import com.ssafy.motif.coreapi.global.response.Cursors;
import com.ssafy.motif.domain.core.comment.CommentReplyCount;
import com.ssafy.motif.domain.core.comment.VideoCommentRepository;
import com.ssafy.motif.domain.core.user.UserActivityKeyRow;
import com.ssafy.motif.domain.core.user.UserActivityRepository;
import com.ssafy.motif.domain.core.video.ParticipationVideoRepository;
import com.ssafy.motif.domain.core.video.VideoFeedRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 내 활동 조회 (API 명세 §4.9).
 *
 * <p><b>{@link UserQueryService} 에 섞지 않는다.</b> 그쪽은 프로필·구독·영상 그리드를
 * 조립하는데, 이 화면은 좋아요·댓글·답글이라 읽는 테이블이 전혀 겹치지 않는다
 * (§11.9 를 {@code UserMotifPassQueryService} 로 뺀 것과 같은 기준).
 *
 * <p>── 왜 이 구조인가 ──────────────────────────────────────────────
 *
 * <p><b>쿼리 수가 페이지 크기와 무관하게 고정이다.</b> 순서 1 + 카드 1 + 좋아요 1 +
 * 내 댓글 1 + 답글 수 1 + 답글 프리뷰 1 + 부모 요약 1 = <b>최대 7</b>. 카드마다 댓글을
 * 읽으면 20건짜리 페이지가 그대로 수십 번의 왕복이 된다.
 *
 * <p><b>제외 판정은 순서 쿼리 한 곳에만 있다</b>({@code UserActivityRepository}). 여기서
 * 한 번 더 거르면 "목록에는 있는데 카드가 비는" 페이지가 생긴다 — 걸러진 만큼 페이지가
 * 짧아지고, 그 상태로 커서를 내보내면 클라이언트는 남은 자리를 알 방법이 없다.
 *
 * <p>읽기 전용 트랜잭션이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserActivityQueryService {

    /** §1.9 커서 목록의 크기 상한. */
    private static final int MAX_SIZE = 100;

    /**
     * 답글 프리뷰 개수 (§4.9 기본안 — 클라 확인 대기, 원본 §16).
     *
     * <p>화면 사정이라 리포지토리가 아니라 여기 둔다({@code UserQueryService.PREVIEW_SIZE}
     * 와 같은 자리). 전체는 {@code myComment.replyCount} 와 함께 게시물 댓글 화면이 맡는다.
     */
    private static final int REPLY_PREVIEW_SIZE = 2;

    private final UserActivityRepository userActivityRepository;
    private final ParticipationVideoRepository participationVideoRepository;
    private final VideoCommentRepository videoCommentRepository;
    private final MediaUrlResolver mediaUrlResolver;

    /**
     * §4.9 내 활동 — 커서 페이지, <b>마지막 상호작용 최신순</b>.
     *
     * <p>실패 분기가 없다. 활동이 0건이면 빈 목록이고, 망가진 커서만 400 이다
     * ({@link Cursors#decodeInteracted} 가 던진다 — 조용히 첫 페이지로 되돌리지 않는다).
     */
    public CursorResponse<ActivityItem> activities(Integer userSeq, String cursor, int size) {
        int limit = Math.clamp(size, 1, MAX_SIZE);
        Cursors.Interacted after = Cursors.decodeInteracted(cursor);

        // limit+1 을 읽어 "다음이 있나" 를 판정한다 — count 쿼리를 따로 쏘지 않는다
        List<UserActivityKeyRow> rows = after == null
                ? userActivityRepository.findActivityKeys(userSeq, limit + 1)
                : userActivityRepository.findActivityKeysAfter(
                        userSeq, after.at(), after.lastSeq(), limit + 1);

        boolean hasNext = rows.size() > limit;
        List<UserActivityKeyRow> keys = hasNext ? rows.subList(0, limit) : rows;
        if (keys.isEmpty()) {
            return CursorResponse.forward(List.of(), null);
        }

        List<Long> videoSeqs = keys.stream().map(UserActivityKeyRow::getVideoSeq).toList();
        List<ActivityItem> list = assemble(userSeq, keys, videoSeqs);

        UserActivityKeyRow last = keys.getLast();
        String nextCursor = hasNext
                ? Cursors.encodeInteracted(last.getLastInteractedDttm(), last.getVideoSeq())
                : null;
        return CursorResponse.forward(list, nextCursor);
    }

    /**
     * 배치 조회 6번을 모아 카드를 만든다.
     *
     * <p><b>순서는 {@code keys} 가 갖는다.</b> {@code in} 절은 순서를 보장하지 않으므로
     * 배치 결과를 {@code Map} 으로 받아 순서 쿼리의 결과로 다시 세운다(§8.1 과 같은 방식).
     */
    private List<ActivityItem> assemble(Integer userSeq, List<UserActivityKeyRow> keys,
                                        List<Long> videoSeqs) {
        Map<Long, VideoFeedRow> cards = participationVideoRepository.findFeedCards(videoSeqs)
                .stream()
                .collect(Collectors.toMap(VideoFeedRow::videoSeq, Function.identity()));
        Set<Long> liked = Set.copyOf(
                participationVideoRepository.findLikedVideoSeqs(userSeq, videoSeqs));
        Map<Long, VideoCommentRepository.MyCommentRow> myComments =
                videoCommentRepository.findMyLatestByVideos(userSeq, videoSeqs).stream()
                        .collect(Collectors.toMap(
                                VideoCommentRepository.MyCommentRow::getVideoSeq,
                                Function.identity()));

        // 루트 댓글에만 답글이 달린다 — 답글에 답글은 없다(깊이 2). 답글인 내 댓글은
        // 부모 요약 쪽으로 간다
        List<Long> rootSeqs = myComments.values().stream()
                .filter(row -> row.getParentCommentSeq() == null)
                .map(VideoCommentRepository.MyCommentRow::getCommentSeq)
                .toList();
        List<Long> parentSeqs = myComments.values().stream()
                .map(VideoCommentRepository.MyCommentRow::getParentCommentSeq)
                .filter(Objects::nonNull)
                .toList();

        Map<Long, Long> replyCounts = replyCounts(rootSeqs);
        Map<Long, List<ActivityItem.Reply>> replies = replies(rootSeqs);
        Map<Long, ActivityItem.ParentComment> parents = parents(parentSeqs);

        return keys.stream()
                .map(key -> item(key, cards.get(key.getVideoSeq()),
                        liked.contains(key.getVideoSeq()),
                        myComments.get(key.getVideoSeq()),
                        replyCounts, replies, parents))
                .filter(Objects::nonNull)
                .toList();
    }

    /** 빈 {@code in} 절은 DB 마다 취급이 달라 아예 보내지 않는다(§4.7 과 같은 이유). */
    private Map<Long, Long> replyCounts(Collection<Long> rootSeqs) {
        if (rootSeqs.isEmpty()) {
            return Map.of();
        }
        return videoCommentRepository.countRepliesByParents(rootSeqs).stream()
                .collect(Collectors.toMap(CommentReplyCount::commentSeq, CommentReplyCount::count));
    }

    private Map<Long, List<ActivityItem.Reply>> replies(Collection<Long> rootSeqs) {
        if (rootSeqs.isEmpty()) {
            return Map.of();
        }
        return videoCommentRepository.findReplyPreviews(rootSeqs, REPLY_PREVIEW_SIZE).stream()
                .collect(Collectors.groupingBy(
                        VideoCommentRepository.ReplyPreviewRow::getParentSeq,
                        // 쿼리가 이미 등록순으로 정렬해 왔다 — groupingBy 는 그 순서를 지킨다
                        Collectors.mapping(row -> new ActivityItem.Reply(
                                row.getCommentSeq(),
                                new ActivityItem.Author(
                                        row.getAuthorUserSeq(),
                                        row.getAuthorNickname(),
                                        mediaUrlResolver.resolve(row.getAuthorProfileImageKey())),
                                row.getContent(),
                                row.getRegDttm()), Collectors.toList())));
    }

    private Map<Long, ActivityItem.ParentComment> parents(Collection<Long> parentSeqs) {
        if (parentSeqs.isEmpty()) {
            return Map.of();
        }
        return videoCommentRepository.findParentSummaries(parentSeqs).stream()
                .collect(Collectors.toMap(
                        VideoCommentRepository.ParentCommentRow::getCommentSeq,
                        row -> new ActivityItem.ParentComment(
                                row.getCommentSeq(),
                                new ActivityItem.Author(
                                        row.getAuthorUserSeq(),
                                        row.getAuthorNickname(),
                                        mediaUrlResolver.resolve(row.getAuthorProfileImageKey())),
                                row.getContent())));
    }

    /**
     * @param card 순서 쿼리와 같은 트랜잭션이라 보통 채워진다. {@code null} 을 허용하는
     *             것은 카드 조회가 {@code composite_jobs} 를 조인하기 때문이다 — 그 행이
     *             비면 활동 전체가 500 이 되는 대신 그 한 장만 빠진다
     */
    private ActivityItem item(UserActivityKeyRow key, VideoFeedRow card, boolean liked,
                              VideoCommentRepository.MyCommentRow myComment,
                              Map<Long, Long> replyCounts,
                              Map<Long, List<ActivityItem.Reply>> replies,
                              Map<Long, ActivityItem.ParentComment> parents) {
        if (card == null) {
            return null;
        }
        return new ActivityItem(
                new ActivityItem.Video(
                        card.videoSeq(),
                        mediaUrlResolver.resolve(card.thumbnailKey()),
                        card.viewCount(),
                        card.likeCount()),
                liked,
                myComment(myComment, replyCounts, parents),
                myComment == null || myComment.getParentCommentSeq() != null
                        ? List.of()
                        : replies.getOrDefault(myComment.getCommentSeq(), List.of()),
                key.getLastInteractedDttm());
    }

    /** 좋아요만 한 영상이면 {@code null} 이다 — 카드는 하트만으로도 성립한다. */
    private ActivityItem.MyComment myComment(VideoCommentRepository.MyCommentRow row,
                                             Map<Long, Long> replyCounts,
                                             Map<Long, ActivityItem.ParentComment> parents) {
        if (row == null) {
            return null;
        }
        Long parentSeq = row.getParentCommentSeq();
        return new ActivityItem.MyComment(
                row.getCommentSeq(),
                row.getContent(),
                row.getLikeCount(),
                replyCounts.getOrDefault(row.getCommentSeq(), 0L),
                row.getRegDttm(),
                parentSeq == null ? null : parents.get(parentSeq));
    }
}
