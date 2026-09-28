package com.ssafy.motif.coreapi.domain.comment;

import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.domain.comment.dto.CommentItem;
import com.ssafy.motif.coreapi.domain.comment.service.CommentQueryService;
import com.ssafy.motif.coreapi.domain.comment.service.CommentReportService;
import com.ssafy.motif.coreapi.domain.report.ReportErrorCode;
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
 * §9.6 댓글 신고를 실제 DB 에 대고 검증한다 (S15P21D104-161).
 *
 * <p><b>DB 제약이 계약의 일부다.</b> 4대상 XOR CHECK 와 UNIQUE 4종은 애플리케이션이
 * 아니라 테이블이 지키는 규칙이라, 네이티브 INSERT 문이 칸을 잘못 채우면 여기서만
 * 드러난다. 중복 판정(409)도 같은 이유로 실제 DB 없이는 검증되지 않는다.
 *
 * <p><b>로컬 compose DB 는 여러 레인이 공유한다</b> — 단언은 이 테스트가 만든 신고로만
 * 좁힌다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class CommentReportLocalDbTest {

    @Autowired
    private CommentReportService commentReportService;
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
    @DisplayName("접수되면 대응 칸 하나만 채워진다 — 나머지 세 칸은 NULL")
    void insertsExactlyOneTarget() {
        Long comment = data.challengeComment(challengeSeq).author(fan).content("댓글").insert();

        commentReportService.report(me, "challenge", comment, "SPAM");

        assertThat(reportRows("challenge_comment_seq", comment)).isEqualTo(1);
        assertThat(nullTargetColumns("challenge_comment_seq", comment)).isEqualTo(3);
        assertThat(reasonCode("challenge_comment_seq", comment)).isEqualTo("SPAM");
    }

    @Test
    @DisplayName("같은 댓글을 두 번 신고하면 409")
    void duplicateIsConflict() {
        Long comment = data.challengeComment(challengeSeq).author(fan).content("댓글").insert();
        commentReportService.report(me, "challenge", comment, "SPAM");

        assertThatThrownBy(() -> commentReportService.report(me, "challenge", comment, "ABUSE"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ReportErrorCode.ALREADY_REPORTED);

        assertThat(reportRows("challenge_comment_seq", comment)).isEqualTo(1);
    }

    @Test
    @DisplayName("남이 신고한 댓글은 나도 신고할 수 있다 — 1인 1대상 1회다")
    void perReporter() {
        Long comment = data.challengeComment(challengeSeq).author(fan).content("댓글").insert();

        commentReportService.report(me, "challenge", comment, "SPAM");
        Integer other = videos.newUser();
        commentReportService.report(other, "challenge", comment, "SPAM");

        assertThat(reportRows("challenge_comment_seq", comment)).isEqualTo(2);
    }

    @Test
    @DisplayName("타입이 다르면 서로 간섭하지 않는다 — UNIQUE 4종은 NULL 로 갈린다")
    void targetsDoNotInterfere() {
        Integer noticeSeq = data.newNotice(artistSeq);
        Long videoSeq = videos.video(challengeSeq, artistSeq).owner(fan).insert();

        Long challengeComment = data.challengeComment(challengeSeq).author(fan).content("챌린지")
                .insert();
        Long videoComment = data.videoComment(videoSeq).author(fan).content("영상").insert();
        Long noticeComment = data.noticeComment(noticeSeq).author(fan).content("공지").insert();

        commentReportService.report(me, "challenge", challengeComment, "SPAM");
        commentReportService.report(me, "video", videoComment, "SPAM");
        commentReportService.report(me, "notice", noticeComment, "SPAM");

        assertThat(reportRows("challenge_comment_seq", challengeComment)).isEqualTo(1);
        assertThat(reportRows("video_comment_seq", videoComment)).isEqualTo(1);
        assertThat(reportRows("notice_comment_seq", noticeComment)).isEqualTo(1);
    }

    @Test
    @DisplayName("답글도 신고할 수 있고, 본인 댓글도 신고할 수 있다")
    void replyAndOwnCommentAreReportable() {
        Long root = data.challengeComment(challengeSeq).author(me).content("내 루트").insert();
        Long reply = data.challengeComment(challengeSeq).author(fan).content("답글")
                .replyTo(root).insert();

        commentReportService.report(me, "challenge", root, "SPAM");
        commentReportService.report(me, "challenge", reply, "SPAM");

        assertThat(reportRows("challenge_comment_seq", root)).isEqualTo(1);
        assertThat(reportRows("challenge_comment_seq", reply)).isEqualTo(1);
    }

    @Test
    @DisplayName("잠긴 영상의 댓글도 신고된다 — 좋아요(§9.5)와 다른 점")
    void lockedVideoCommentIsReportable() {
        Long videoSeq = videos.video(challengeSeq, artistSeq).owner(me).insert();
        Long comment = data.videoComment(videoSeq).author(fan).content("영상 댓글").insert();
        data.disableComments(videoSeq);

        commentReportService.report(me, "video", comment, "ABUSE");

        assertThat(reportRows("video_comment_seq", comment)).isEqualTo(1);
    }

    @Test
    @DisplayName("접수는 접수일 뿐이다 — 댓글이 목록에서 사라지지 않는다")
    void reportingDoesNotHideComment() {
        Long comment = data.challengeComment(challengeSeq).author(fan).content("댓글")
                .likes(2).insert();

        commentReportService.report(me, "challenge", comment, "SPAM");

        CommentItem item = commentQueryService.challengeComments(me, challengeSeq, 0, 20)
                .list().getFirst();
        assertThat(item.commentSeq()).isEqualTo(comment);
        assertThat(item.likeCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("보이지 않는 댓글은 404 — 삭제·탈퇴 작성자·감춰진 스레드의 답글")
    void invisibleCommentsAreNotFound() {
        Long deleted = data.challengeComment(challengeSeq).author(fan).content("삭제된 댓글")
                .deleted().insert();
        assertNotFound(deleted);

        Integer leaver = videos.newUser();
        Long leaverRoot = data.challengeComment(challengeSeq).author(leaver).content("탈퇴자 루트")
                .insert();
        Long replyUnderLeaver = data.challengeComment(challengeSeq).author(fan)
                .content("탈퇴자 루트의 답글").replyTo(leaverRoot).insert();
        data.deleteUser(leaver);

        assertNotFound(leaverRoot);
        assertNotFound(replyUnderLeaver);
    }

    @Test
    @DisplayName("미존재·모르는 type·대상 비노출은 404")
    void unknownTargetsAreNotFound() {
        Long comment = data.challengeComment(challengeSeq).author(fan).content("댓글").insert();

        assertNotFound(-1L);
        assertThatThrownBy(() -> commentReportService.report(me, "song", comment, "SPAM"))
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

        assertThatThrownBy(() -> commentReportService.report(me, "video", comment, "SPAM"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommentErrorCode.COMMENT_NOT_FOUND);
    }

    private void assertNotFound(Long commentSeq) {
        assertThatThrownBy(() -> commentReportService.report(me, "challenge", commentSeq, "SPAM"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommentErrorCode.COMMENT_NOT_FOUND);
    }

    private int reportRows(String column, Long commentSeq) {
        return ((Number) entityManager
                .createNativeQuery("select count(*) from motif_core.reports where "
                        + column + " = ?1")
                .setParameter(1, commentSeq)
                .getSingleResult()).intValue();
    }

    /** 채워진 칸 하나를 뺀 나머지 대상 칸이 몇 개나 NULL 인지 — XOR CHECK 의 실제 결과. */
    private int nullTargetColumns(String filled, Long commentSeq) {
        return ((Number) entityManager
                .createNativeQuery("""
                        select (video_seq is null)::int
                             + (challenge_comment_seq is null)::int
                             + (video_comment_seq is null)::int
                             + (notice_comment_seq is null)::int
                          from motif_core.reports where %s = ?1
                        """.formatted(filled))
                .setParameter(1, commentSeq)
                .getSingleResult()).intValue();
    }

    private String reasonCode(String column, Long commentSeq) {
        return (String) entityManager
                .createNativeQuery("select reason_code from motif_core.reports where "
                        + column + " = ?1")
                .setParameter(1, commentSeq)
                .getSingleResult();
    }
}
