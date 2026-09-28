package com.ssafy.motif.coreapi.domain.comment;

import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.domain.comment.dto.CommentCreateRequest;
import com.ssafy.motif.coreapi.domain.comment.dto.CommentItem;
import com.ssafy.motif.coreapi.domain.comment.dto.ReplyItem;
import com.ssafy.motif.coreapi.domain.comment.service.CommentCommandService;
import com.ssafy.motif.coreapi.domain.comment.service.CommentLikeService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * §9.5 댓글 좋아요 토글을 실제 DB 에 대고 검증한다 (S15P21D104-154).
 *
 * <p><b>멱등이 이 API 의 핵심이다.</b> 같은 요청을 두 번 받아도 카운터가 한 번만
 * 움직여야 하고, 해제를 두 번 받아도 음수가 되면 안 된다 — 후자는 DB CHECK 위반으로
 * 터지는 자리라 단위 테스트로는 드러나지 않는다.
 *
 * <p>결과는 <b>읽기 API 로</b> 확인한다(§9.1 {@code isLiked}·{@code likeCount}). 204 라
 * 응답에 아무것도 없어서, 목록에 반영되는지가 이 API 의 관측 가능한 전부다.
 *
 * <p><b>로컬 compose DB 는 여러 레인이 공유한다</b> — 단언은 이 테스트가 만든 댓글로만
 * 좁힌다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class CommentLikeLocalDbTest {

    @Autowired
    private CommentLikeService commentLikeService;
    @Autowired
    private CommentCommandService commentCommandService;
    @Autowired
    private CommentQueryService commentQueryService;
    @PersistenceContext
    private EntityManager entityManager;

    private CommentTestData data;
    private VideoTestData videos;

    private Integer artistSeq;
    private Integer challengeSeq;
    private Integer me;
    private Integer fan;

    @BeforeEach
    void setUp() {
        data = new CommentTestData(entityManager);
        videos = data.videos();

        artistSeq = videos.newArtist();
        me = videos.newUser();
        fan = videos.newUser();
        challengeSeq = videos.newChallenge(artistSeq, videos.newSong(me), false);
    }

    @Test
    @DisplayName("좋아요는 멱등 — 두 번 눌러도 카운터는 한 번만 오른다")
    void likeIsIdempotent() {
        Long comment = data.challengeComment(challengeSeq).author(fan).content("댓글").insert();

        commentLikeService.like(me, "challenge", comment);
        commentLikeService.like(me, "challenge", comment);

        CommentItem item = firstChallengeComment();
        assertThat(item.isLiked()).isTrue();
        assertThat(item.likeCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("해제도 멱등 — 누르지 않은 상태에서 두 번 해제해도 0 이고 음수가 되지 않는다")
    void unlikeIsIdempotentAndNeverNegative() {
        Long comment = data.challengeComment(challengeSeq).author(fan).content("댓글").insert();

        commentLikeService.like(me, "challenge", comment);
        commentLikeService.unlike(me, "challenge", comment);
        commentLikeService.unlike(me, "challenge", comment);
        commentLikeService.unlike(me, "challenge", comment);

        CommentItem item = firstChallengeComment();
        assertThat(item.isLiked()).isFalse();
        assertThat(item.likeCount()).isZero();
    }

    @Test
    @DisplayName("남의 좋아요는 내 isLiked 가 아니다 — 카운터는 함께 오른다")
    void likeIsPerUser() {
        Long comment = data.challengeComment(challengeSeq).author(fan).content("댓글").insert();

        commentLikeService.like(fan, "challenge", comment);
        assertThat(firstChallengeComment().isLiked()).isFalse();
        assertThat(firstChallengeComment().likeCount()).isEqualTo(1);

        commentLikeService.like(me, "challenge", comment);
        assertThat(firstChallengeComment().isLiked()).isTrue();
        assertThat(firstChallengeComment().likeCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("답글에도 누를 수 있다 — §9.2 에 반영된다")
    void likeOnReply() {
        Long root = data.challengeComment(challengeSeq).author(fan).content("루트").insert();
        Long reply = data.challengeComment(challengeSeq).author(fan).content("답글")
                .replyTo(root).insert();

        commentLikeService.like(me, "challenge", reply);

        ReplyItem item = commentQueryService.replies(me, "challenge", root, 0, 20).list().getFirst();
        assertThat(item.commentSeq()).isEqualTo(reply);
        assertThat(item.isLiked()).isTrue();
        assertThat(item.likeCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("자기 댓글에도 누를 수 있다")
    void likeOwnComment() {
        Long mine = commentCommandService
                .writeOnChallenge(me, challengeSeq, new CommentCreateRequest("내 댓글", null))
                .commentSeq();

        commentLikeService.like(me, "challenge", mine);

        assertThat(firstChallengeComment().isLiked()).isTrue();
    }

    @Test
    @DisplayName("잠긴 영상은 생성·해제 모두 403 — 잠그기 전에 누른 것도 못 뗀다")
    void lockedVideoBlocksBothDirections() {
        Long videoSeq = videos.video(challengeSeq, artistSeq).owner(me).insert();
        Long comment = data.videoComment(videoSeq).author(fan).content("영상 댓글").insert();
        commentLikeService.like(me, "video", comment);

        data.disableComments(videoSeq);

        assertThatThrownBy(() -> commentLikeService.like(me, "video", comment))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommentErrorCode.COMMENT_DISABLED);

        assertThatThrownBy(() -> commentLikeService.unlike(me, "video", comment))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommentErrorCode.COMMENT_DISABLED);
    }

    @Test
    @DisplayName("보이지 않는 댓글은 404 — 삭제·탈퇴 작성자·감춰진 스레드의 답글")
    void invisibleCommentsAreNotFound() {
        Long deleted = data.challengeComment(challengeSeq).author(fan).content("삭제된 댓글")
                .deleted().insert();
        assertNotFound(deleted);

        Integer leaver = videos.newUser();
        Long leaverComment = data.challengeComment(challengeSeq).author(leaver)
                .content("탈퇴자 댓글").insert();
        Long replyUnderLeaver = data.challengeComment(challengeSeq).author(fan)
                .content("탈퇴자 루트의 답글").replyTo(leaverComment).insert();
        data.deleteUser(leaver);

        assertNotFound(leaverComment);
        // 답글 자체는 멀쩡하지만 스레드가 통째로 감춰져 있다 (§1.8)
        assertNotFound(replyUnderLeaver);
    }

    @Test
    @DisplayName("미존재·모르는 type·대상 비노출은 404")
    void unknownTargetsAreNotFound() {
        Long comment = data.challengeComment(challengeSeq).author(fan).content("댓글").insert();

        assertNotFound(-1L);
        assertThatThrownBy(() -> commentLikeService.like(me, "song", comment))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommentErrorCode.COMMENT_NOT_FOUND);

        entityManager.createNativeQuery(
                        "update motif_core.challenges set is_deleted = true where challenge_seq = ?1")
                .setParameter(1, challengeSeq)
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();

        assertNotFound(comment);
    }

    @Test
    @DisplayName("타인의 PRIVATE 영상 댓글은 404 — 존재가 드러나지 않는다")
    void otherPrivateVideoIsNotFound() {
        Long videoSeq = videos.video(challengeSeq, artistSeq).owner(fan)
                .privateVisibility().insert();
        Long comment = data.videoComment(videoSeq).author(fan).content("댓글").insert();

        assertThatThrownBy(() -> commentLikeService.like(me, "video", comment))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommentErrorCode.COMMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("공지 댓글도 같은 규칙으로 돈다")
    void noticeCommentLike() {
        Integer noticeSeq = data.newNotice(artistSeq);
        Long comment = data.noticeComment(noticeSeq).author(fan).content("공지 댓글").insert();

        commentLikeService.like(me, "notice", comment);
        CommentItem liked = commentQueryService.noticeComments(me, noticeSeq, 0, 20)
                .list().getFirst();
        assertThat(liked.isLiked()).isTrue();
        assertThat(liked.likeCount()).isEqualTo(1);

        commentLikeService.unlike(me, "notice", comment);
        CommentItem unliked = commentQueryService.noticeComments(me, noticeSeq, 0, 20)
                .list().getFirst();
        assertThat(unliked.isLiked()).isFalse();
        assertThat(unliked.likeCount()).isZero();
    }

    @Test
    @DisplayName("§9.4 로 지운 댓글에는 더 이상 누를 수 없다 — 좋아요 행도 남지 않는다")
    void deletedCommentRejectsLike() {
        Long mine = commentCommandService
                .writeOnChallenge(me, challengeSeq, new CommentCreateRequest("내 댓글", null))
                .commentSeq();
        commentLikeService.like(fan, "challenge", mine);

        commentCommandService.delete(me, "challenge", mine);

        assertNotFound(mine);
        assertThat(likeRows(mine)).isZero();
    }

    private CommentItem firstChallengeComment() {
        return commentQueryService.challengeComments(me, challengeSeq, 0, 20).list().getFirst();
    }

    private void assertNotFound(Long commentSeq) {
        assertThatThrownBy(() -> commentLikeService.like(me, "challenge", commentSeq))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommentErrorCode.COMMENT_NOT_FOUND);
        assertThatThrownBy(() -> commentLikeService.unlike(me, "challenge", commentSeq))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommentErrorCode.COMMENT_NOT_FOUND);
    }

    private int likeRows(Long commentSeq) {
        return ((Number) entityManager
                .createNativeQuery("select count(*) from motif_core.challenge_comment_likes"
                        + " where challenge_comment_seq = ?1")
                .setParameter(1, commentSeq)
                .getSingleResult()).intValue();
    }
}
