package com.ssafy.motif.coreapi.domain.comment;

import com.ssafy.motif.common.error.CommonErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.domain.comment.dto.CommentCreateRequest;
import com.ssafy.motif.coreapi.domain.comment.dto.CommentItem;
import com.ssafy.motif.coreapi.domain.comment.dto.ReplyItem;
import com.ssafy.motif.coreapi.domain.comment.service.CommentCommandService;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * §9.3 작성 · §9.4 삭제를 실제 DB 에 대고 검증한다.
 *
 * <p><b>읽기 API 와 한 쌍으로 단언한다.</b> 쓰기의 결과는 목록(§9.1)·답글(§9.2)에서
 * 보이는 모양이 전부다 — 행만 확인하면 "저장은 됐는데 목록에 안 나오는" 상태를 통과
 * 시킨다. 거울 카운터({@code comment_count})와 {@code has_artist_comment} 는 조회
 * API 가 쓰지 않는 값이라 네이티브로 직접 본다.
 *
 * <p><b>로컬 compose DB 는 여러 레인이 공유한다</b> — 단언은 이 테스트가 만든 대상으로만
 * 좁힌다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class CommentWriteDeleteLocalDbTest {

    @Autowired
    private CommentCommandService commentCommandService;
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
    @DisplayName("작성하면 목록에 보이고 commentCount 가 오른다")
    void writeAppearsInList() {
        Long seq = commentCommandService
                .writeOnChallenge(me, challengeSeq, new CommentCreateRequest("잘 봤어요!", null))
                .commentSeq();

        List<CommentItem> list = commentQueryService.challengeComments(me, challengeSeq, 0, 20)
                .list();

        assertThat(list).extracting(CommentItem::commentSeq).containsExactly(seq);
        assertThat(list.getFirst().content()).isEqualTo("잘 봤어요!");
        assertThat(list.getFirst().isMine()).isTrue();
        assertThat(challengeCommentCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("답글은 목록에 안 나오고 replyCount 로 접힌다 — 펼치면 보인다")
    void replyIsFolded() {
        Long root = commentCommandService
                .writeOnChallenge(fan, challengeSeq, new CommentCreateRequest("루트", null))
                .commentSeq();
        Long reply = commentCommandService
                .writeOnChallenge(me, challengeSeq, new CommentCreateRequest("답글", root))
                .commentSeq();

        CommentItem folded = commentQueryService.challengeComments(me, challengeSeq, 0, 20)
                .list().getFirst();
        List<ReplyItem> expanded = commentQueryService.replies(me, "challenge", root, 0, 20).list();

        assertThat(folded.commentSeq()).isEqualTo(root);
        assertThat(folded.replyCount()).isEqualTo(1);
        assertThat(expanded).extracting(ReplyItem::commentSeq).containsExactly(reply);
        // 루트 1 + 답글 1 — 카운터는 답글도 센다
        assertThat(challengeCommentCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("답글에 답글은 400, 다른 게시물의 부모는 400")
    void parentRulesAreEnforced() {
        Long root = commentCommandService
                .writeOnChallenge(fan, challengeSeq, new CommentCreateRequest("루트", null))
                .commentSeq();
        Long reply = commentCommandService
                .writeOnChallenge(me, challengeSeq, new CommentCreateRequest("답글", root))
                .commentSeq();

        assertThatThrownBy(() -> commentCommandService.writeOnChallenge(
                me, challengeSeq, new CommentCreateRequest("답글의 답글", reply)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommentErrorCode.REPLY_DEPTH_EXCEEDED);

        Integer otherChallenge = videos.newChallenge(artistSeq, videos.newSong(me), false);
        assertThatThrownBy(() -> commentCommandService.writeOnChallenge(
                me, otherChallenge, new CommentCreateRequest("남의 스레드", root)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommentErrorCode.PARENT_TARGET_MISMATCH);
    }

    @Test
    @DisplayName("삭제된 부모·탈퇴 작성자의 루트에는 답글을 달 수 없다")
    void hiddenParentRejectsReply() {
        Long deletedRoot = data.challengeComment(challengeSeq).author(fan).content("삭제된 루트")
                .deleted().insert();
        assertThatThrownBy(() -> commentCommandService.writeOnChallenge(
                me, challengeSeq, new CommentCreateRequest("답글", deletedRoot)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommentErrorCode.COMMENT_NOT_FOUND);

        Integer leaver = videos.newUser();
        Long leaverRoot = data.challengeComment(challengeSeq).author(leaver).content("탈퇴자 루트")
                .insert();
        data.deleteUser(leaver);

        assertThatThrownBy(() -> commentCommandService.writeOnChallenge(
                me, challengeSeq, new CommentCreateRequest("답글", leaverRoot)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommentErrorCode.COMMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("잠긴 영상에는 쓸 수 없다 — 그래도 지울 수는 있다")
    void lockBlocksWriteButNotDelete() {
        Long videoSeq = videos.video(challengeSeq, artistSeq).owner(me).insert();
        Long mine = commentCommandService
                .writeOnVideo(me, videoSeq, new CommentCreateRequest("내 댓글", null))
                .commentSeq();
        data.disableComments(videoSeq);

        assertThatThrownBy(() -> commentCommandService.writeOnVideo(
                me, videoSeq, new CommentCreateRequest("잠긴 뒤", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommentErrorCode.COMMENT_DISABLED);

        assertThat(commentCommandService.delete(me, "video", mine).deletedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("아티스트가 달면 hasArtistComment 가 켜지고, 마지막 하나를 지우면 꺼진다")
    void artistCommentFlag() {
        Long videoSeq = videos.video(challengeSeq, artistSeq).owner(me).insert();

        commentCommandService.writeOnVideo(me, videoSeq, new CommentCreateRequest("팬 댓글", null));
        assertThat(hasArtistComment(videoSeq)).isFalse();

        Long first = commentCommandService
                .writeOnVideo(artistUserSeq, videoSeq, new CommentCreateRequest("아티스트1", null))
                .commentSeq();
        Long second = commentCommandService
                .writeOnVideo(artistUserSeq, videoSeq, new CommentCreateRequest("아티스트2", null))
                .commentSeq();
        assertThat(hasArtistComment(videoSeq)).isTrue();

        commentCommandService.delete(artistUserSeq, "video", first);
        assertThat(hasArtistComment(videoSeq)).isTrue();   // 하나 남았다 — 끄면 안 된다

        commentCommandService.delete(artistUserSeq, "video", second);
        assertThat(hasArtistComment(videoSeq)).isFalse();
    }

    @Test
    @DisplayName("루트를 지우면 직계 답글도 지워진다 — deletedCount 와 카운터 감소폭이 같다")
    void deletingRootDeletesReplies() {
        Long root = commentCommandService
                .writeOnChallenge(me, challengeSeq, new CommentCreateRequest("루트", null))
                .commentSeq();
        commentCommandService.writeOnChallenge(fan, challengeSeq,
                new CommentCreateRequest("답글1", root));
        commentCommandService.writeOnChallenge(fan, challengeSeq,
                new CommentCreateRequest("답글2", root));
        // 이미 지워져 있던 답글은 세지 않는다
        data.challengeComment(challengeSeq).author(fan).content("이미 삭제된 답글")
                .replyTo(root).deleted().insert();
        assertThat(challengeCommentCount()).isEqualTo(3);

        int deleted = commentCommandService.delete(me, "challenge", root).deletedCount();

        assertThat(deleted).isEqualTo(3);
        assertThat(challengeCommentCount()).isZero();
        assertThat(commentQueryService.challengeComments(me, challengeSeq, 0, 20).list()).isEmpty();
    }

    @Test
    @DisplayName("지워진 댓글의 좋아요는 하드 삭제된다")
    void deletingRemovesLikes() {
        Long root = commentCommandService
                .writeOnChallenge(me, challengeSeq, new CommentCreateRequest("루트", null))
                .commentSeq();
        Long reply = commentCommandService
                .writeOnChallenge(me, challengeSeq, new CommentCreateRequest("답글", root))
                .commentSeq();
        data.likeChallengeComment(root, fan);
        data.likeChallengeComment(reply, fan);
        assertThat(likeRows(List.of(root, reply))).isEqualTo(2);

        commentCommandService.delete(me, "challenge", root);

        assertThat(likeRows(List.of(root, reply))).isZero();
    }

    @Test
    @DisplayName("답글만 지우면 그것만 지워진다 — 루트는 남는다")
    void deletingReplyKeepsRoot() {
        Long root = commentCommandService
                .writeOnChallenge(fan, challengeSeq, new CommentCreateRequest("루트", null))
                .commentSeq();
        Long reply = commentCommandService
                .writeOnChallenge(me, challengeSeq, new CommentCreateRequest("답글", root))
                .commentSeq();

        assertThat(commentCommandService.delete(me, "challenge", reply).deletedCount()).isEqualTo(1);
        assertThat(challengeCommentCount()).isEqualTo(1);

        CommentItem folded = commentQueryService.challengeComments(me, challengeSeq, 0, 20)
                .list().getFirst();
        assertThat(folded.commentSeq()).isEqualTo(root);
        assertThat(folded.replyCount()).isZero();
    }

    @Test
    @DisplayName("남의 댓글은 403, 이미 지운 댓글은 404 — 멱등이 아니다")
    void deleteGuards() {
        Long others = commentCommandService
                .writeOnChallenge(fan, challengeSeq, new CommentCreateRequest("남의 댓글", null))
                .commentSeq();
        Long mine = commentCommandService
                .writeOnChallenge(me, challengeSeq, new CommentCreateRequest("내 댓글", null))
                .commentSeq();

        assertThatThrownBy(() -> commentCommandService.delete(me, "challenge", others))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.FORBIDDEN);

        commentCommandService.delete(me, "challenge", mine);
        assertThatThrownBy(() -> commentCommandService.delete(me, "challenge", mine))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommentErrorCode.COMMENT_NOT_FOUND);

        assertThatThrownBy(() -> commentCommandService.delete(me, "song", mine))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommentErrorCode.COMMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("공지 — 작성·답글·삭제가 같은 규칙으로 돈다")
    void noticeFlow() {
        Integer noticeSeq = data.newNotice(artistSeq);

        Long root = commentCommandService
                .writeOnNotice(me, noticeSeq, new CommentCreateRequest("공지 댓글", null))
                .commentSeq();
        commentCommandService.writeOnNotice(fan, noticeSeq, new CommentCreateRequest("답글", root));
        assertThat(noticeCommentCount(noticeSeq)).isEqualTo(2);

        assertThat(commentCommandService.delete(me, "notice", root).deletedCount()).isEqualTo(2);
        assertThat(noticeCommentCount(noticeSeq)).isZero();
        assertThat(commentQueryService.noticeComments(me, noticeSeq, 0, 20).list()).isEmpty();
    }

    @Test
    @DisplayName("삭제된 챌린지의 댓글은 지울 수 없다 — 404")
    void invisibleTargetBlocksDelete() {
        Long mine = commentCommandService
                .writeOnChallenge(me, challengeSeq, new CommentCreateRequest("내 댓글", null))
                .commentSeq();
        entityManager.createNativeQuery(
                        "update motif_core.challenges set is_deleted = true where challenge_seq = ?1")
                .setParameter(1, challengeSeq)
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();

        assertThatThrownBy(() -> commentCommandService.delete(me, "challenge", mine))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommentErrorCode.COMMENT_NOT_FOUND);
    }

    private int challengeCommentCount() {
        return ((Number) entityManager
                .createNativeQuery("select comment_count from motif_core.challenges"
                        + " where challenge_seq = ?1")
                .setParameter(1, challengeSeq)
                .getSingleResult()).intValue();
    }

    private int noticeCommentCount(Integer noticeSeq) {
        return ((Number) entityManager
                .createNativeQuery("select comment_count from motif_core.notices"
                        + " where notice_seq = ?1")
                .setParameter(1, noticeSeq)
                .getSingleResult()).intValue();
    }

    private boolean hasArtistComment(Long videoSeq) {
        return (Boolean) entityManager
                .createNativeQuery("select has_artist_comment from motif_core.participation_videos"
                        + " where video_seq = ?1")
                .setParameter(1, videoSeq)
                .getSingleResult();
    }

    private int likeRows(List<Long> commentSeqs) {
        return ((Number) entityManager
                .createNativeQuery("select count(*) from motif_core.challenge_comment_likes"
                        + " where challenge_comment_seq in (?1)")
                .setParameter(1, commentSeqs)
                .getSingleResult()).intValue();
    }
}
