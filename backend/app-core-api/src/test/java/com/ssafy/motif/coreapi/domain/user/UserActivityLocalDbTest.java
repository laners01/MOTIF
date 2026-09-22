package com.ssafy.motif.coreapi.domain.user;

import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.common.response.CursorResponse;
import com.ssafy.motif.coreapi.domain.comment.CommentTestData;
import com.ssafy.motif.coreapi.domain.user.dto.ActivityItem;
import com.ssafy.motif.coreapi.domain.user.service.UserActivityQueryService;
import com.ssafy.motif.coreapi.domain.video.VideoTestData;
import com.ssafy.motif.domain.core.user.ActivityTargetType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * §4.9 내 활동을 실제 DB 에 대고 검증한다 (S15P21D104-298).
 *
 * <p><b>이 API 의 어려운 부분은 조립이 아니라 집합이다</b> — 좋아요와 댓글을 대상 단위로
 * 접는 것, 무엇을 목록에서 빼는 것, 커서가 그 경계를 정확히 자르는 것. 그래서 단언도
 * 거기에 몰려 있다.
 *
 * <p>대상이 <b>참여 영상과 챌린지 원본 둘</b>이라 제외 조건이 대상마다 다르다 — 참여
 * 영상 쪽 세 조건(본인 게시물·PRIVATE·댓글 잠금)에 대응하는 칼럼이 {@code challenges}
 * 에는 없다. 그래서 양쪽을 따로 단언한다.
 *
 * <p><b>로컬 compose DB 는 여러 레인이 공유한다</b> — 매번 새 사용자를 만들어 조회하므로
 * 결과에는 이 테스트가 만든 활동만 들어온다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class UserActivityLocalDbTest {

    @Autowired
    private UserActivityQueryService userActivityQueryService;
    @PersistenceContext
    private EntityManager entityManager;

    private CommentTestData data;
    private VideoTestData videos;

    private Integer challengeSeq;
    private Integer songSeq;
    private Integer artistSeq;
    private Integer me;
    private Integer owner;
    private Integer fan;

    private final Instant base = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    @BeforeEach
    void setUp() {
        data = new CommentTestData(entityManager);
        videos = data.videos();

        artistSeq = videos.newArtist();
        me = videos.newUser();
        owner = videos.newUser();
        fan = videos.newUser();
        songSeq = videos.newSong(me);
        challengeSeq = videos.newChallenge(artistSeq, songSeq, false);
    }

    // ── 참여 영상 대상 ────────────────────────────────────────────────

    @Test
    @DisplayName("좋아요와 댓글이 둘 다 있으면 카드 1장으로 병합하고, 시각은 나중 것을 쓴다")
    void mergesLikeAndCommentIntoOneCard() {
        Long video = video().views(3400).likes(12000).insert();
        likeVideo(video, me, base.minus(2, ChronoUnit.HOURS));
        data.videoComment(video).author(me).content("내 댓글").at(base).insert();

        List<ActivityItem> list = activities().list();

        assertThat(list).hasSize(1);
        ActivityItem card = list.getFirst();
        assertThat(card.target().type()).isEqualTo(ActivityTargetType.VIDEO);
        assertThat(card.target().seq()).isEqualTo(video);
        assertThat(card.target().viewCount()).isEqualTo(3400L);
        assertThat(card.target().likeCount()).isEqualTo(12000);
        assertThat(card.liked()).isTrue();
        assertThat(card.myComment().content()).isEqualTo("내 댓글");
        // 좋아요가 2시간 빨랐다 — 마지막 상호작용은 댓글이다
        assertThat(card.lastInteractedDttm()).isEqualTo(base);
    }

    @Test
    @DisplayName("좋아요만 한 영상은 myComment 가 null 이고 replies 는 빈 배열이다")
    void likeOnlyCardHasNoComment() {
        Long video = video().insert();
        likeVideo(video, me, base);

        ActivityItem card = activities().list().getFirst();

        assertThat(card.liked()).isTrue();
        assertThat(card.myComment()).isNull();
        assertThat(card.replies()).isEmpty();
        assertThat(card.lastInteractedDttm()).isEqualTo(base);
    }

    @Test
    @DisplayName("같은 영상에 내 댓글이 여럿이면 최신 1개만 싣는다")
    void keepsOnlyLatestOfMyComments() {
        Long video = video().insert();
        data.videoComment(video).author(me).content("옛날 것")
                .at(base.minus(1, ChronoUnit.HOURS)).insert();
        data.videoComment(video).author(me).content("최신 것").at(base).insert();

        ActivityItem card = activities().list().getFirst();

        assertThat(card.myComment().content()).isEqualTo("최신 것");
    }

    @Test
    @DisplayName("내 댓글에 달린 답글은 최신 2건을 고르고 등록순으로 싣는다 — replyCount 는 전량")
    void repliesArePreviewedLatestTwoInRegistrationOrder() {
        Long video = video().insert();
        Long mine = data.videoComment(video).author(me).content("내 루트 댓글").at(base).insert();
        data.videoComment(video).author(fan).replyTo(mine).content("첫 답글")
                .at(base.plus(1, ChronoUnit.MINUTES)).insert();
        data.videoComment(video).author(fan).replyTo(mine).content("둘째 답글")
                .at(base.plus(2, ChronoUnit.MINUTES)).insert();
        data.videoComment(video).author(fan).replyTo(mine).content("셋째 답글")
                .at(base.plus(3, ChronoUnit.MINUTES)).insert();

        ActivityItem card = activities().list().getFirst();

        assertThat(card.myComment().replyCount()).isEqualTo(3);
        // 고르는 것은 최신 2건(둘째·셋째), 싣는 것은 등록순
        assertThat(card.replies()).extracting(ActivityItem.Reply::content)
                .containsExactly("둘째 답글", "셋째 답글");
        assertThat(card.replies().getFirst().author().userSeq()).isEqualTo(fan);
    }

    @Test
    @DisplayName("내 댓글이 답글이면 부모 요약이 붙고 replies 는 항상 빈 배열이다")
    void replyOfMineCarriesParentAndNoReplies() {
        Long video = video().insert();
        Long parent = data.videoComment(video).author(fan).content("부모 댓글").at(base).insert();
        data.videoComment(video).author(me).replyTo(parent).content("내 답글")
                .at(base.plus(1, ChronoUnit.MINUTES)).insert();

        ActivityItem card = activities().list().getFirst();

        assertThat(card.myComment().content()).isEqualTo("내 답글");
        assertThat(card.myComment().parentComment().commentSeq()).isEqualTo(parent);
        assertThat(card.myComment().parentComment().content()).isEqualTo("부모 댓글");
        assertThat(card.myComment().parentComment().author().userSeq()).isEqualTo(fan);
        assertThat(card.replies()).isEmpty();
    }

    @Test
    @DisplayName("내 댓글만 삭제되면 카드는 남고 myComment 만 null 이 된다")
    void deletedCommentLeavesTheLikedCard() {
        Long video = video().insert();
        likeVideo(video, me, base);
        data.videoComment(video).author(me).content("지워진 댓글").deleted().at(base).insert();

        List<ActivityItem> list = activities().list();

        assertThat(list).hasSize(1);
        assertThat(list.getFirst().liked()).isTrue();
        assertThat(list.getFirst().myComment()).isNull();
    }

    @Test
    @DisplayName("본인 게시물은 제외한다 — 내 영상 그리드는 §4.8 이 맡는다")
    void excludesMyOwnVideo() {
        Long mineVideo = videos.video(challengeSeq, artistSeq).owner(me).insert();
        likeVideo(mineVideo, me, base);

        assertThat(activities().list()).isEmpty();
    }

    @Test
    @DisplayName("삭제된 영상은 제외한다")
    void excludesDeletedVideo() {
        Long video = video().deleted().insert();
        likeVideo(video, me, base);

        assertThat(activities().list()).isEmpty();
    }

    @Test
    @DisplayName("타인의 PRIVATE 영상은 제외한다")
    void excludesOthersPrivateVideo() {
        Long video = video().privateVisibility().insert();
        likeVideo(video, me, base);

        assertThat(activities().list()).isEmpty();
    }

    @Test
    @DisplayName("댓글 사용 중지된 영상은 좋아요만 눌렀어도 카드째 제외한다 (ACTIVITY-02)")
    void excludesCommentDisabledVideoEvenWhenOnlyLiked() {
        Long video = video().insert();
        likeVideo(video, me, base);
        data.disableComments(video);

        assertThat(activities().list()).isEmpty();
    }

    // ── 챌린지 원본 대상 ──────────────────────────────────────────────

    @Test
    @DisplayName("챌린지 원본에 누른 좋아요·댓글도 카드가 된다")
    void challengeIsACardToo() {
        counts(challengeSeq, 940L, 77);
        likeChallenge(challengeSeq, me, base.minus(2, ChronoUnit.HOURS));
        data.challengeComment(challengeSeq).author(me).content("챌린지에 단 댓글")
                .at(base).insert();

        List<ActivityItem> list = activities().list();

        assertThat(list).hasSize(1);
        ActivityItem card = list.getFirst();
        assertThat(card.target().type()).isEqualTo(ActivityTargetType.CHALLENGE);
        assertThat(card.target().seq()).isEqualTo(challengeSeq.longValue());
        assertThat(card.target().viewCount()).isEqualTo(940L);
        assertThat(card.target().likeCount()).isEqualTo(77);
        assertThat(card.liked()).isTrue();
        assertThat(card.myComment().content()).isEqualTo("챌린지에 단 댓글");
        assertThat(card.lastInteractedDttm()).isEqualTo(base);
    }

    @Test
    @DisplayName("챌린지 댓글에도 답글 프리뷰와 부모 요약이 붙는다")
    void challengeCommentCarriesRepliesAndParent() {
        Long mine = data.challengeComment(challengeSeq).author(me).content("내 루트 댓글")
                .at(base).insert();
        data.challengeComment(challengeSeq).author(fan).replyTo(mine).content("첫 답글")
                .at(base.plus(1, ChronoUnit.MINUTES)).insert();
        data.challengeComment(challengeSeq).author(fan).replyTo(mine).content("둘째 답글")
                .at(base.plus(2, ChronoUnit.MINUTES)).insert();

        Integer other = videos.newChallenge(artistSeq, songSeq, false);
        Long parent = data.challengeComment(other).author(fan).content("부모 댓글")
                .at(base.minus(1, ChronoUnit.HOURS)).insert();
        data.challengeComment(other).author(me).replyTo(parent).content("내 답글")
                .at(base.minus(30, ChronoUnit.MINUTES)).insert();

        List<ActivityItem> list = activities().list();

        assertThat(list).hasSize(2);
        ActivityItem root = list.getFirst();
        assertThat(root.myComment().replyCount()).isEqualTo(2);
        assertThat(root.replies()).extracting(ActivityItem.Reply::content)
                .containsExactly("첫 답글", "둘째 답글");

        ActivityItem reply = list.get(1);
        assertThat(reply.myComment().content()).isEqualTo("내 답글");
        assertThat(reply.myComment().parentComment().content()).isEqualTo("부모 댓글");
        assertThat(reply.replies()).isEmpty();
    }

    @Test
    @DisplayName("삭제된 챌린지는 제외한다")
    void excludesDeletedChallenge() {
        Integer deleted = videos.newChallenge(artistSeq, songSeq, true);
        likeChallenge(deleted, me, base);

        assertThat(activities().list()).isEmpty();
    }

    @Test
    @DisplayName("챌린지는 게시자가 없어 '본인 게시물 제외' 의 대상이 아니다")
    void challengeIsNeverExcludedAsMyOwnPost() {
        // 이 챌린지의 곡은 내가 등록한 것이다 — 그래도 카드는 남는다
        likeChallenge(challengeSeq, me, base);

        assertThat(activities().list()).hasSize(1);
    }

    // ── 두 대상이 섞이는 목록 ──────────────────────────────────────────

    @Test
    @DisplayName("영상과 챌린지가 한 목록에 마지막 상호작용 최신순으로 섞인다")
    void bothTargetsShareOneOrderedList() {
        Long video = video().insert();
        likeVideo(video, me, base.minus(1, ChronoUnit.HOURS));
        likeChallenge(challengeSeq, me, base);

        List<ActivityItem> list = activities().list();

        assertThat(list).extracting(item -> item.target().type())
                .containsExactly(ActivityTargetType.CHALLENGE, ActivityTargetType.VIDEO);
        assertThat(list).extracting(item -> item.target().seq())
                .containsExactly(challengeSeq.longValue(), video);
    }

    @Test
    @DisplayName("같은 시각에 상호작용한 영상과 챌린지도 커서가 정확히 가른다")
    void cursorSplitsTiesAcrossTargets() {
        Long video = video().insert();
        likeVideo(video, me, base);
        likeChallenge(challengeSeq, me, base);

        CursorResponse<ActivityItem> page1 = userActivityQueryService.activities(me, null, 1);
        assertThat(page1.hasNext()).isTrue();
        assertThat(page1.list()).hasSize(1);

        CursorResponse<ActivityItem> page2 =
                userActivityQueryService.activities(me, page1.nextCursor(), 1);
        assertThat(page2.hasNext()).isFalse();
        assertThat(page2.list()).hasSize(1);

        // 둘이 각자 한 번씩만 나왔다 — 중복도 누락도 없다
        assertThat(List.of(page1.list().getFirst(), page2.list().getFirst()))
                .extracting(item -> item.target().type())
                .containsExactlyInAnyOrder(ActivityTargetType.VIDEO, ActivityTargetType.CHALLENGE);
    }

    // ── 커서·빈 목록 ──────────────────────────────────────────────────

    @Test
    @DisplayName("커서로 이어 받으면 중복도 누락도 없다")
    void cursorPagesWithoutOverlapOrGap() {
        Long first = video().insert();
        Long second = video().insert();
        Long third = video().insert();
        likeVideo(first, me, base.minus(3, ChronoUnit.HOURS));
        likeVideo(second, me, base.minus(2, ChronoUnit.HOURS));
        likeVideo(third, me, base.minus(1, ChronoUnit.HOURS));

        CursorResponse<ActivityItem> page1 =
                userActivityQueryService.activities(me, null, 2);
        assertThat(page1.hasNext()).isTrue();
        assertThat(seqs(page1)).containsExactly(third, second);

        CursorResponse<ActivityItem> page2 =
                userActivityQueryService.activities(me, page1.nextCursor(), 2);
        assertThat(page2.hasNext()).isFalse();
        assertThat(page2.nextCursor()).isNull();
        assertThat(seqs(page2)).containsExactly(first);
    }

    @Test
    @DisplayName("망가진 커서는 400 이다 — 조용히 첫 페이지로 되돌리지 않는다")
    void brokenCursorIsRejected() {
        assertThatThrownBy(() -> userActivityQueryService.activities(me, "!!not-base64!!", 20))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("활동이 없으면 빈 목록이다")
    void emptyWhenNothingDone() {
        CursorResponse<ActivityItem> response = activities();

        assertThat(response.list()).isEmpty();
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
    }

    private CursorResponse<ActivityItem> activities() {
        return userActivityQueryService.activities(me, null, 20);
    }

    private static List<Long> seqs(CursorResponse<ActivityItem> response) {
        return response.list().stream().map(item -> item.target().seq()).toList();
    }

    /** 남이 올린 공개 영상 — 참여 영상 쪽이 카드로 만드는 유일한 모양이다. */
    private VideoTestData.VideoFixture video() {
        return videos.video(challengeSeq, artistSeq).owner(owner);
    }

    /**
     * {@code video_likes} 행을 직접 세운다.
     *
     * <p>§8.3 토글 서비스를 부르지 않는 것은 {@code reg_dttm} 을 지정해야 하기 때문이다 —
     * 이 API 의 정렬 기준이 그 값이라 시각을 흔들지 않고는 순서를 단언할 수 없다.
     */
    private void likeVideo(Long videoSeq, Integer userSeq, Instant at) {
        execute("""
                insert into motif_core.video_likes (video_seq, user_seq, reg_dttm)
                values (?1, ?2, ?3)
                """, videoSeq, userSeq, at);
    }

    /** {@code challenge_likes} 행 — {@link #likeVideo} 와 같은 이유로 직접 세운다. */
    private void likeChallenge(Integer challengeSeq, Integer userSeq, Instant at) {
        execute("""
                insert into motif_core.challenge_likes (challenge_seq, user_seq, reg_dttm)
                values (?1, ?2, ?3)
                """, challengeSeq, userSeq, at);
    }

    /** 카드에 실리는 챌린지 카운터 — 등록 픽스처가 0 으로만 넣는다. */
    private void counts(Integer challengeSeq, Long viewCount, Integer likeCount) {
        execute("""
                update motif_core.challenges set view_count = ?2, like_count = ?3
                 where challenge_seq = ?1
                """, challengeSeq, viewCount, likeCount);
    }

    private void execute(String sql, Object... params) {
        var query = entityManager.createNativeQuery(sql);
        for (int i = 0; i < params.length; i++) {
            query.setParameter(i + 1, params[i]);
        }
        query.executeUpdate();
        entityManager.flush();
        entityManager.clear();
    }
}
