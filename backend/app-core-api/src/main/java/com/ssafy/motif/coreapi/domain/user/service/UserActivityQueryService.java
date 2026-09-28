package com.ssafy.motif.coreapi.domain.user.service;

import com.ssafy.motif.common.response.CursorResponse;
import com.ssafy.motif.coreapi.domain.user.dto.ActivityItem;
import com.ssafy.motif.coreapi.global.media.MediaUrlResolver;
import com.ssafy.motif.coreapi.global.response.Cursors;
import com.ssafy.motif.domain.core.challenge.ChallengeActivityRow;
import com.ssafy.motif.domain.core.challenge.ChallengeRepository;
import com.ssafy.motif.domain.core.comment.ChallengeCommentRepository;
import com.ssafy.motif.domain.core.comment.CommentReplyCount;
import com.ssafy.motif.domain.core.comment.VideoCommentRepository;
import com.ssafy.motif.domain.core.user.ActivityTargetType;
import com.ssafy.motif.domain.core.user.UserActivityKeyRow;
import com.ssafy.motif.domain.core.user.UserActivityRepository;
import com.ssafy.motif.domain.core.video.ParticipationVideoRepository;
import com.ssafy.motif.domain.core.video.VideoFeedRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 내 활동 조회 (API 명세 §4.9).
 *
 * <p><b>{@link UserQueryService} 에 섞지 않는다.</b> 그쪽은 프로필·구독·영상 그리드를
 * 조립하는데, 이 화면은 좋아요·댓글·답글이라 읽는 테이블이 전혀 겹치지 않는다
 * (§11.9 를 {@code UserMotifPassQueryService} 로 뺀 것과 같은 기준).
 *
 * <p>── 왜 이 구조인가 ──────────────────────────────────────────────
 *
 * <p><b>쿼리 수가 페이지 크기와 무관하게 고정이다.</b> 순서 1 + 대상별로 (카드 1 +
 * 좋아요 1 + 내 댓글 1 + 답글 수 1 + 답글 프리뷰 1 + 부모 요약 1) = <b>최대 13</b>.
 * 카드마다 댓글을 읽으면 20건짜리 페이지가 그대로 수십 번의 왕복이 된다. 한쪽 대상만
 * 실린 페이지는 그 쪽 6개만 돈다.
 *
 * <p><b>제외 판정은 순서 쿼리 한 곳에만 있다</b>({@code UserActivityRepository}). 여기서
 * 한 번 더 거르면 "목록에는 있는데 카드가 비는" 페이지가 생긴다 — 걸러진 만큼 페이지가
 * 짧아지고, 그 상태로 커서를 내보내면 클라이언트는 남은 자리를 알 방법이 없다.
 *
 * <p><b>배치 결과는 전부 {@link TargetKey} 로 색인한다.</b> seq 하나로 색인하면 12번
 * 영상과 12번 챌린지가 같은 칸을 쓴다 — 두 대상은 테이블이 달라 seq 공간이 겹친다.
 * 댓글 seq 도 마찬가지라({@code video_comments} ↔ {@code challenge_comments}) 댓글
 * 딸림 정보까지 대상 단위로 접어서 들고 있는다.
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
    private final ChallengeRepository challengeRepository;
    private final VideoCommentRepository videoCommentRepository;
    private final ChallengeCommentRepository challengeCommentRepository;
    private final MediaUrlResolver mediaUrlResolver;

    /**
     * §4.9 내 활동 — 커서 페이지, <b>마지막 상호작용 최신순</b>.
     *
     * <p>참여 영상과 챌린지 원본이 <b>한 목록에 섞여</b> 온다. 어느 쪽인지는 카드의
     * {@code target.type} 이 말한다.
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
                        userSeq, after.at(), after.type().name(), after.lastSeq(), limit + 1);

        boolean hasNext = rows.size() > limit;
        List<UserActivityKeyRow> keys = hasNext ? rows.subList(0, limit) : rows;
        if (keys.isEmpty()) {
            return CursorResponse.forward(List.of(), null);
        }

        List<ActivityItem> list = assemble(userSeq, keys);

        TargetKey last = TargetKey.of(keys.getLast());
        String nextCursor = hasNext
                ? Cursors.encodeInteracted(
                        keys.getLast().getLastInteractedDttm(), last.type(), last.seq())
                : null;
        return CursorResponse.forward(list, nextCursor);
    }

    /**
     * 대상별 배치 조회를 모아 카드를 만든다.
     *
     * <p><b>순서는 {@code keys} 가 갖는다.</b> {@code in} 절은 순서를 보장하지 않으므로
     * 배치 결과를 {@code Map} 으로 받아 순서 쿼리의 결과로 다시 세운다(§8.1 과 같은 방식).
     *
     * <p>한쪽 대상이 이 페이지에 없으면 그쪽 쿼리는 아예 보내지 않는다 — 빈
     * {@code in} 절은 DB 마다 취급이 다르다(§4.7 과 같은 이유).
     */
    private List<ActivityItem> assemble(Integer userSeq, List<UserActivityKeyRow> keys) {
        List<Long> videoSeqs = new ArrayList<>();
        List<Integer> challengeSeqs = new ArrayList<>();
        for (UserActivityKeyRow key : keys) {
            if (ActivityTargetType.valueOf(key.getTargetType()) == ActivityTargetType.VIDEO) {
                videoSeqs.add(key.getTargetSeq());
            } else {
                challengeSeqs.add(key.getTargetSeq().intValue());
            }
        }

        Assembly into = Assembly.empty();
        if (!videoSeqs.isEmpty()) {
            collectVideos(userSeq, videoSeqs, into);
        }
        if (!challengeSeqs.isEmpty()) {
            collectChallenges(userSeq, challengeSeqs, into);
        }

        return keys.stream()
                .map(key -> item(key, into))
                .filter(Objects::nonNull)
                .toList();
    }

    /** 참여 영상 쪽 배치 6번. */
    private void collectVideos(Integer userSeq, List<Long> videoSeqs, Assembly into) {
        for (VideoFeedRow row : participationVideoRepository.findFeedCards(videoSeqs)) {
            into.cards.put(new TargetKey(ActivityTargetType.VIDEO, row.videoSeq()),
                    new Card(row.thumbnailKey(), row.viewCount(), row.likeCount()));
        }
        for (Long seq : participationVideoRepository.findLikedVideoSeqs(userSeq, videoSeqs)) {
            into.liked.add(new TargetKey(ActivityTargetType.VIDEO, seq));
        }

        // 내 댓글의 seq → 그 댓글이 달린 대상. 답글 수·프리뷰·부모 요약을 대상 단위로
        // 되접는 색인이다 — 댓글 seq 를 그대로 키로 쓰면 챌린지 쪽 댓글과 부딪힌다
        Map<Long, TargetKey> byComment = new HashMap<>();
        List<Long> rootSeqs = new ArrayList<>();
        Map<TargetKey, Long> parentOf = new HashMap<>();
        for (VideoCommentRepository.MyCommentRow row
                : videoCommentRepository.findMyLatestByVideos(userSeq, videoSeqs)) {
            TargetKey key = new TargetKey(ActivityTargetType.VIDEO, row.getVideoSeq());
            into.myComments.put(key, new MyCommentData(row.getCommentSeq(), row.getContent(),
                    row.getLikeCount(), row.getParentCommentSeq(), row.getRegDttm()));
            byComment.put(row.getCommentSeq(), key);
            // 루트 댓글에만 답글이 달린다 — 답글에 답글은 없다(깊이 2). 답글인 내 댓글은
            // 부모 요약 쪽으로 간다
            if (row.getParentCommentSeq() == null) {
                rootSeqs.add(row.getCommentSeq());
            } else {
                parentOf.put(key, row.getParentCommentSeq());
            }
        }

        if (!rootSeqs.isEmpty()) {
            for (CommentReplyCount count : videoCommentRepository.countRepliesByParents(rootSeqs)) {
                into.replyCounts.put(byComment.get(count.commentSeq()), count.count());
            }
            for (VideoCommentRepository.ReplyPreviewRow row
                    : videoCommentRepository.findReplyPreviews(rootSeqs, REPLY_PREVIEW_SIZE)) {
                // 쿼리가 이미 등록순으로 정렬해 왔다 — 순서대로 덧붙이면 그 순서가 남는다
                into.replies.computeIfAbsent(byComment.get(row.getParentSeq()),
                                k -> new ArrayList<>())
                        .add(new ActivityItem.Reply(
                                row.getCommentSeq(),
                                author(row.getAuthorUserSeq(), row.getAuthorNickname(),
                                        row.getAuthorProfileImageKey()),
                                row.getContent(),
                                row.getRegDttm()));
            }
        }

        if (!parentOf.isEmpty()) {
            Map<Long, ActivityItem.ParentComment> summaries = new HashMap<>();
            for (VideoCommentRepository.ParentCommentRow row
                    : videoCommentRepository.findParentSummaries(parentOf.values())) {
                summaries.put(row.getCommentSeq(), new ActivityItem.ParentComment(
                        row.getCommentSeq(),
                        author(row.getAuthorUserSeq(), row.getAuthorNickname(),
                                row.getAuthorProfileImageKey()),
                        row.getContent()));
            }
            putParents(parentOf, summaries, into);
        }
    }

    /**
     * 챌린지 원본 쪽 배치 6번 — 구조는 {@link #collectVideos} 와 같다.
     *
     * <p>둘을 제네릭으로 합치지 않는다. 읽는 저장소가 다르고 투영 인터페이스도
     * 별개라(대상 seq 게터의 이름·타입이 다르다) 합치려면 어댑터가 한 겹 더 생기는데,
     * 그 어댑터가 하는 일이 지금 여기 보이는 것과 같은 분량이다.
     */
    private void collectChallenges(Integer userSeq, List<Integer> challengeSeqs, Assembly into) {
        for (ChallengeActivityRow row : challengeRepository.findActivityCards(challengeSeqs)) {
            into.cards.put(key(row.challengeSeq()),
                    new Card(row.thumbnailKey(), row.viewCount(), row.likeCount()));
        }
        for (Integer seq : challengeRepository.findLikedChallengeSeqs(userSeq, challengeSeqs)) {
            into.liked.add(key(seq));
        }

        Map<Long, TargetKey> byComment = new HashMap<>();
        List<Long> rootSeqs = new ArrayList<>();
        Map<TargetKey, Long> parentOf = new HashMap<>();
        for (ChallengeCommentRepository.MyCommentRow row
                : challengeCommentRepository.findMyLatestByChallenges(userSeq, challengeSeqs)) {
            TargetKey key = key(row.getChallengeSeq());
            into.myComments.put(key, new MyCommentData(row.getCommentSeq(), row.getContent(),
                    row.getLikeCount(), row.getParentCommentSeq(), row.getRegDttm()));
            byComment.put(row.getCommentSeq(), key);
            if (row.getParentCommentSeq() == null) {
                rootSeqs.add(row.getCommentSeq());
            } else {
                parentOf.put(key, row.getParentCommentSeq());
            }
        }

        if (!rootSeqs.isEmpty()) {
            for (CommentReplyCount count
                    : challengeCommentRepository.countRepliesByParents(rootSeqs)) {
                into.replyCounts.put(byComment.get(count.commentSeq()), count.count());
            }
            for (ChallengeCommentRepository.ReplyPreviewRow row
                    : challengeCommentRepository.findReplyPreviews(rootSeqs, REPLY_PREVIEW_SIZE)) {
                into.replies.computeIfAbsent(byComment.get(row.getParentSeq()),
                                k -> new ArrayList<>())
                        .add(new ActivityItem.Reply(
                                row.getCommentSeq(),
                                author(row.getAuthorUserSeq(), row.getAuthorNickname(),
                                        row.getAuthorProfileImageKey()),
                                row.getContent(),
                                row.getRegDttm()));
            }
        }

        if (!parentOf.isEmpty()) {
            Map<Long, ActivityItem.ParentComment> summaries = new HashMap<>();
            for (ChallengeCommentRepository.ParentCommentRow row
                    : challengeCommentRepository.findParentSummaries(parentOf.values())) {
                summaries.put(row.getCommentSeq(), new ActivityItem.ParentComment(
                        row.getCommentSeq(),
                        author(row.getAuthorUserSeq(), row.getAuthorNickname(),
                                row.getAuthorProfileImageKey()),
                        row.getContent()));
            }
            putParents(parentOf, summaries, into);
        }
    }

    /**
     * 부모 요약을 <b>대상 단위</b>로 옮긴다.
     *
     * <p>부모가 삭제됐거나 작성자가 탈퇴했으면 요약에 행이 없다 — 그때는 넣지 않고,
     * 카드의 {@code parentComment} 가 {@code null} 로 남는다. 카드는 남는다: 내가 단
     * 답글은 그대로 있고 사라진 것은 그 위 맥락뿐이다.
     */
    private void putParents(Map<TargetKey, Long> parentOf,
                            Map<Long, ActivityItem.ParentComment> summaries, Assembly into) {
        parentOf.forEach((key, parentSeq) -> {
            ActivityItem.ParentComment summary = summaries.get(parentSeq);
            if (summary != null) {
                into.parents.put(key, summary);
            }
        });
    }

    private ActivityItem.Author author(Integer userSeq, String nickname, String profileImageKey) {
        return new ActivityItem.Author(userSeq, nickname, mediaUrlResolver.resolveProfile(profileImageKey));
    }

    private static TargetKey key(Integer challengeSeq) {
        return new TargetKey(ActivityTargetType.CHALLENGE, challengeSeq.longValue());
    }

    /**
     * 카드 한 장.
     *
     * <p>본문이 없으면({@code card == null}) 그 한 장만 빠진다. 참여 영상 쪽 카드 조회가
     * {@code composite_jobs} 를 조인하기 때문에 생기는 여지인데, 그 행이 비면 활동
     * 전체가 500 이 되는 대신 한 장을 버린다.
     */
    private ActivityItem item(UserActivityKeyRow row, Assembly into) {
        TargetKey key = TargetKey.of(row);
        Card card = into.cards.get(key);
        if (card == null) {
            return null;
        }
        MyCommentData myComment = into.myComments.get(key);
        return new ActivityItem(
                new ActivityItem.Target(
                        key.type(),
                        key.seq(),
                        mediaUrlResolver.resolve(card.thumbnailKey()),
                        card.viewCount(),
                        card.likeCount()),
                into.liked.contains(key),
                myComment(key, myComment, into),
                myComment == null || myComment.parentCommentSeq() != null
                        ? List.of()
                        : into.replies.getOrDefault(key, List.of()),
                row.getLastInteractedDttm());
    }

    /** 좋아요만 한 대상이면 {@code null} 이다 — 카드는 하트만으로도 성립한다. */
    private ActivityItem.MyComment myComment(TargetKey key, MyCommentData data, Assembly into) {
        if (data == null) {
            return null;
        }
        return new ActivityItem.MyComment(
                data.commentSeq(),
                data.content(),
                data.likeCount(),
                into.replyCounts.getOrDefault(key, 0L),
                data.regDttm(),
                data.parentCommentSeq() == null ? null : into.parents.get(key));
    }

    /**
     * 배치 결과의 색인 키 — <b>대상의 종류와 seq 가 같이 있어야 유일하다</b>.
     *
     * <p>{@code participation_videos} 와 {@code challenges} 는 각자 IDENTITY 를 쓰므로
     * 12번이 양쪽에 다 있다. seq 만으로 색인하면 한쪽 카드가 다른 쪽 썸네일·좋아요를
     * 집어 간다 — 예외도 로그도 없이 내용만 바뀌는 종류다.
     */
    private record TargetKey(ActivityTargetType type, Long seq) {

        static TargetKey of(UserActivityKeyRow row) {
            return new TargetKey(ActivityTargetType.valueOf(row.getTargetType()),
                    row.getTargetSeq());
        }
    }

    /** 카드 본문 — 두 대상이 각자 다른 테이블에서 같은 세 칸을 준다. */
    private record Card(String thumbnailKey, Long viewCount, Integer likeCount) {
    }

    /** 내 최신 댓글 — 두 저장소의 투영을 한 모양으로 받아 둔 것. */
    private record MyCommentData(Long commentSeq, String content, Integer likeCount,
                                 Long parentCommentSeq, Instant regDttm) {
    }

    /**
     * 배치 결과를 모으는 자리 — 두 대상이 <b>같은 통에</b> 넣는다.
     *
     * <p>대상별로 통을 나누면 카드를 세울 때 어느 통을 볼지 다시 갈라야 하는데, 그
     * 분기는 이미 {@link TargetKey} 가 품고 있다.
     */
    private static final class Assembly {

        private final Map<TargetKey, Card> cards = new HashMap<>();
        private final Set<TargetKey> liked = new HashSet<>();
        private final Map<TargetKey, MyCommentData> myComments = new HashMap<>();
        private final Map<TargetKey, Long> replyCounts = new HashMap<>();
        private final Map<TargetKey, List<ActivityItem.Reply>> replies = new HashMap<>();
        private final Map<TargetKey, ActivityItem.ParentComment> parents = new HashMap<>();

        static Assembly empty() {
            return new Assembly();
        }
    }
}
