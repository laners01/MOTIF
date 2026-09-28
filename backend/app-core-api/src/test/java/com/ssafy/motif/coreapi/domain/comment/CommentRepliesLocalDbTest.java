package com.ssafy.motif.coreapi.domain.comment;

import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.common.response.PageResponse;
import com.ssafy.motif.coreapi.domain.comment.dto.CommentItem;
import com.ssafy.motif.coreapi.domain.comment.dto.ReplyItem;
import com.ssafy.motif.coreapi.domain.comment.service.CommentQueryService;
import com.ssafy.motif.coreapi.domain.video.VideoTestData;
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
import static org.assertj.core.api.Assertions.tuple;

/**
 * §9.2 답글 펼치기를 실제 DB 에 대고 검증한다 (S15P21D104-153).
 *
 * <p><b>§9.1 과 한 쌍으로 봐야 하는 단언이 있다</b> — 접힌 수({@code replyCount})와 펼친
 * 수({@code totalElements})가 같은지다. 두 API 가 비노출 규칙을 각자 걸기 때문에 한쪽만
 * 고치면 "2개라고 써 놓고 펼치면 3개" 가 조용히 만들어진다.
 *
 * <p><b>로컬 compose DB 는 여러 레인이 공유한다</b> — 단언은 이 테스트가 만든 댓글로만
 * 좁힌다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class CommentRepliesLocalDbTest {

    @Autowired
    private CommentQueryService commentQueryService;
    @PersistenceContext
    private EntityManager entityManager;

    private CommentTestData data;
    private VideoTestData videos;

    private Integer artistSeq;
    private Integer artistUserSeq;
    private Integer challengeSeq;
    private Integer me;
    private Integer fan;

    private final Instant base = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    @BeforeEach
    void setUp() {
        data = new CommentTestData(entityManager);
        videos = data.videos();

        artistSeq = videos.newArtist();
        artistUserSeq = data.artistUserSeq(artistSeq);
        me = videos.newUser();
        fan = videos.newUser();
        challengeSeq = videos.newChallenge(artistSeq, videos.newSong(me), false);
    }

    @Test
    @DisplayName("등록순 고정 — 최신순이 아니다")
    void ordersByRegistration() {
        Long root = data.challengeComment(challengeSeq).author(fan).content("루트")
                .at(base.minusSeconds(60)).insert();
        data.challengeComment(challengeSeq).author(fan).content("먼저").replyTo(root)
                .at(base.minusSeconds(30)).insert();
        data.challengeComment(challengeSeq).author(me).content("나중").replyTo(root)
                .at(base.minusSeconds(10)).insert();

        PageResponse<ReplyItem> page = commentQueryService
                .replies(me, "challenge", root, 0, 20);

        assertThat(page.list()).extracting(ReplyItem::content).containsExactly("먼저", "나중");
    }

    @Test
    @DisplayName("항목 — isArtist·isMine·isLiked·likeCount")
    void itemFields() {
        Long root = data.challengeComment(challengeSeq).author(fan).content("루트")
                .at(base.minusSeconds(60)).insert();
        Long artistReply = data.challengeComment(challengeSeq).author(artistUserSeq)
                .content("아티스트 답글").replyTo(root).likes(2).at(base.minusSeconds(30)).insert();
        data.challengeComment(challengeSeq).author(me).content("내 답글").replyTo(root)
                .at(base.minusSeconds(10)).insert();
        data.likeChallengeComment(artistReply, me);

        List<ReplyItem> list = commentQueryService.replies(me, "challenge", root, 0, 20).list();

        assertThat(list).extracting(ReplyItem::content, item -> item.author().isArtist(),
                        ReplyItem::isMine, ReplyItem::isLiked, ReplyItem::likeCount)
                .containsExactly(
                        tuple("아티스트 답글", true, false, true, 2),
                        tuple("내 답글", false, true, false, 0));
    }

    @Test
    @DisplayName("접힌 수(§9.1 replyCount)와 펼친 수(§9.2 totalElements)가 같다")
    void foldedCountMatchesExpanded() {
        Long root = data.challengeComment(challengeSeq).author(fan).content("루트")
                .at(base.minusSeconds(60)).insert();
        Integer leaver = videos.newUser();

        data.challengeComment(challengeSeq).author(me).content("답글1").replyTo(root)
                .at(base.minusSeconds(50)).insert();
        data.challengeComment(challengeSeq).author(fan).content("답글2").replyTo(root)
                .at(base.minusSeconds(40)).insert();
        data.challengeComment(challengeSeq).author(fan).content("삭제된 답글").replyTo(root)
                .deleted().at(base.minusSeconds(30)).insert();
        data.challengeComment(challengeSeq).author(leaver).content("탈퇴자 답글").replyTo(root)
                .at(base.minusSeconds(20)).insert();
        data.deleteUser(leaver);

        CommentItem folded = commentQueryService.challengeComments(me, challengeSeq, 0, 20)
                .list().getFirst();
        PageResponse<ReplyItem> expanded = commentQueryService
                .replies(me, "challenge", root, 0, 20);

        assertThat(expanded.list()).extracting(ReplyItem::content)
                .containsExactly("답글1", "답글2");
        assertThat(expanded.totalElements()).isEqualTo(folded.replyCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("답글의 답글을 펼치려 하면 404 — 루트가 아니다")
    void replyOfReplyIsNotFound() {
        Long root = data.challengeComment(challengeSeq).author(fan).content("루트")
                .at(base.minusSeconds(60)).insert();
        Long reply = data.challengeComment(challengeSeq).author(fan).content("답글").replyTo(root)
                .at(base.minusSeconds(30)).insert();

        assertThatThrownBy(() -> commentQueryService.replies(me, "challenge", reply, 0, 20))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommentErrorCode.COMMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("삭제된 루트·탈퇴 작성자의 루트는 404 — 스레드 전체가 비노출이다")
    void hiddenRootHidesThread() {
        Long deletedRoot = data.challengeComment(challengeSeq).author(fan).content("삭제된 루트")
                .deleted().at(base.minusSeconds(60)).insert();
        data.challengeComment(challengeSeq).author(me).content("답글A").replyTo(deletedRoot)
                .insert();

        Integer leaver = videos.newUser();
        Long leaverRoot = data.challengeComment(challengeSeq).author(leaver).content("탈퇴자 루트")
                .at(base.minusSeconds(50)).insert();
        data.challengeComment(challengeSeq).author(me).content("답글B").replyTo(leaverRoot).insert();
        data.deleteUser(leaver);

        assertThatThrownBy(() -> commentQueryService.replies(me, "challenge", deletedRoot, 0, 20))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommentErrorCode.COMMENT_NOT_FOUND);

        assertThatThrownBy(() -> commentQueryService.replies(me, "challenge", leaverRoot, 0, 20))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommentErrorCode.COMMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("알 수 없는 type 과 미존재 댓글은 404")
    void unknownTypeOrCommentIsNotFound() {
        Long root = data.challengeComment(challengeSeq).author(fan).content("루트").insert();

        assertThatThrownBy(() -> commentQueryService.replies(me, "CHALLENGE", root, 0, 20))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommentErrorCode.COMMENT_NOT_FOUND);

        assertThatThrownBy(() -> commentQueryService.replies(me, "song", root, 0, 20))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommentErrorCode.COMMENT_NOT_FOUND);

        assertThatThrownBy(() -> commentQueryService.replies(me, "challenge", -1L, 0, 20))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommentErrorCode.COMMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("대상이 안 보이면 404 — 삭제된 챌린지·타인의 PRIVATE 영상")
    void invisibleTargetIsCommentNotFound() {
        Integer liveChallenge = videos.newChallenge(artistSeq, videos.newSong(me), false);
        Long root = data.challengeComment(liveChallenge).author(fan).content("루트").insert();
        data.challengeComment(liveChallenge).author(me).content("답글").replyTo(root).insert();
        entityManager.createNativeQuery(
                        "update motif_core.challenges set is_deleted = true where challenge_seq = ?1")
                .setParameter(1, liveChallenge)
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();

        assertThatThrownBy(() -> commentQueryService.replies(me, "challenge", root, 0, 20))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommentErrorCode.COMMENT_NOT_FOUND);

        Long privateVideo = videos.video(challengeSeq, artistSeq).owner(fan)
                .privateVisibility().insert();
        Long videoRoot = data.videoComment(privateVideo).author(fan).content("루트").insert();

        // §9.1 이었다면 VIDEO_NOT_FOUND 다 — 여기서는 commentSeq 만 들고 오므로 그 번호가
        // 어느 테이블의 것인지 드러나지 않게 COMMENT_NOT_FOUND 로 모은다
        assertThatThrownBy(() -> commentQueryService.replies(me, "video", videoRoot, 0, 20))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommentErrorCode.COMMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("참여 영상 — 댓글 잠금은 403 이고 404 로 삼키지 않는다")
    void disabledVideoIsForbidden() {
        Long videoSeq = videos.video(challengeSeq, artistSeq).owner(me).insert();
        Long root = data.videoComment(videoSeq).author(fan).content("루트")
                .at(base.minusSeconds(60)).insert();
        data.videoComment(videoSeq).author(me).content("답글").replyTo(root)
                .at(base.minusSeconds(30)).insert();

        assertThat(commentQueryService.replies(me, "video", root, 0, 20).list())
                .extracting(ReplyItem::content).containsExactly("답글");

        data.disableComments(videoSeq);

        assertThatThrownBy(() -> commentQueryService.replies(me, "video", root, 0, 20))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommentErrorCode.COMMENT_DISABLED);
    }

    @Test
    @DisplayName("공지 — 답글이 나오고, 아티스트 뱃지는 공지 주인 기준이다")
    void noticeReplies() {
        Integer noticeSeq = data.newNotice(artistSeq);
        Long root = data.noticeComment(noticeSeq).author(fan).content("루트")
                .at(base.minusSeconds(60)).insert();
        data.noticeComment(noticeSeq).author(artistUserSeq).content("아티스트 답글").replyTo(root)
                .at(base.minusSeconds(30)).insert();
        data.noticeComment(noticeSeq).author(fan).content("일반 답글").replyTo(root)
                .at(base.minusSeconds(10)).insert();

        List<ReplyItem> list = commentQueryService.replies(me, "notice", root, 0, 20).list();

        assertThat(list).extracting(ReplyItem::content, item -> item.author().isArtist())
                .containsExactly(tuple("아티스트 답글", true), tuple("일반 답글", false));
    }

    @Test
    @DisplayName("답글이 없으면 빈 페이지다 — 루트는 200 으로 열린다")
    void emptyPage() {
        Long root = data.challengeComment(challengeSeq).author(fan).content("루트").insert();

        PageResponse<ReplyItem> page = commentQueryService.replies(me, "challenge", root, 0, 20);

        assertThat(page.list()).isEmpty();
        assertThat(page.totalElements()).isZero();
        assertThat(page.hasNext()).isFalse();
    }

    @Test
    @DisplayName("같은 시각 답글도 페이지 경계에서 겹치거나 빠지지 않는다")
    void pagesAreStableOnTiedTimestamps() {
        Long root = data.challengeComment(challengeSeq).author(fan).content("루트")
                .at(base.minusSeconds(60)).insert();
        for (int i = 1; i <= 5; i++) {
            data.challengeComment(challengeSeq).author(fan).content("답글" + i).replyTo(root)
                    .at(base).insert();
        }

        List<Long> seen = List.of(0, 1, 2).stream()
                .map(p -> commentQueryService.replies(me, "challenge", root, p, 2))
                .flatMap(page -> page.list().stream())
                .map(ReplyItem::commentSeq)
                .toList();

        assertThat(seen).doesNotHaveDuplicates().hasSize(5);
        assertThat(commentQueryService.replies(me, "challenge", root, 0, 2).totalPages())
                .isEqualTo(3);
    }
}
