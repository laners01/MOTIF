package com.ssafy.motif.coreapi.domain.comment;

import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.common.response.PageResponse;
import com.ssafy.motif.coreapi.domain.challenge.ChallengeErrorCode;
import com.ssafy.motif.coreapi.domain.comment.dto.CommentItem;
import com.ssafy.motif.coreapi.domain.comment.service.CommentQueryService;
import com.ssafy.motif.coreapi.domain.notice.NoticeErrorCode;
import com.ssafy.motif.coreapi.domain.video.VideoErrorCode;
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
 * §9.1 루트 댓글 목록을 실제 DB 에 대고 검증한다 (S15P21D104-152).
 *
 * <p><b>정렬이 이 API 의 핵심이다.</b> "① 대상 아티스트 ② 나 ③ 일반, 각 최신순" 은
 * 쿼리가 거는 규칙이라 SQL 없이는 검증되지 않는다 — 특히 <b>다른 아티스트 계정의 댓글이
 * 1순위로 올라오지 않는지</b>는 {@code role} 로 판정했을 때만 깨지는 종류라서, 단위
 * 테스트로는 잡히지 않고 여기서만 잡힌다.
 *
 * <p><b>로컬 compose DB 는 여러 레인이 공유한다</b> — 단언은 이 테스트가 만든 대상의
 * 댓글로만 좁힌다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class CommentListLocalDbTest {

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
    @DisplayName("정렬 — 대상 아티스트 → 나 → 일반, 각 최신순")
    void sortsByThreeGroups() {
        // 일부러 '아티스트가 가장 오래된 댓글' 이고 '남의 아티스트가 가장 최신' 이다.
        // 시각만 보면 정반대 순서가 나오므로 그룹이 실제로 먼저 걸리는지가 드러난다.
        Integer otherArtistUser = data.artistUserSeq(videos.newArtist());

        data.challengeComment(challengeSeq).author(artistUserSeq).content("아티스트")
                .at(base.minusSeconds(50)).insert();
        data.challengeComment(challengeSeq).author(me).content("내 댓글")
                .at(base.minusSeconds(40)).insert();
        data.challengeComment(challengeSeq).author(fan).content("일반-오래")
                .at(base.minusSeconds(30)).insert();
        data.challengeComment(challengeSeq).author(fan).content("일반-최신")
                .at(base.minusSeconds(10)).insert();
        data.challengeComment(challengeSeq).author(otherArtistUser).content("남의 아티스트")
                .at(base.minusSeconds(5)).insert();

        PageResponse<CommentItem> page = commentQueryService
                .challengeComments(me, challengeSeq, 0, 20);

        assertThat(page.list()).extracting(CommentItem::content)
                .containsExactly("아티스트", "내 댓글", "남의 아티스트", "일반-최신", "일반-오래");
        assertThat(page.totalElements()).isEqualTo(5);
    }

    @Test
    @DisplayName("isArtist 는 대상 아티스트만 — 다른 아티스트 계정은 일반 댓글이다")
    void artistBadgeIsTargetArtistOnly() {
        Integer otherArtistUser = data.artistUserSeq(videos.newArtist());

        data.challengeComment(challengeSeq).author(artistUserSeq).content("아티스트")
                .at(base.minusSeconds(30)).insert();
        data.challengeComment(challengeSeq).author(otherArtistUser).content("남의 아티스트")
                .at(base.minusSeconds(20)).insert();
        data.challengeComment(challengeSeq).author(me).content("내 댓글")
                .at(base.minusSeconds(10)).insert();

        List<CommentItem> list = commentQueryService
                .challengeComments(me, challengeSeq, 0, 20).list();

        assertThat(list).extracting(CommentItem::content, item -> item.author().isArtist())
                .containsExactly(
                        tuple("아티스트", true),
                        tuple("내 댓글", false),
                        tuple("남의 아티스트", false));
        assertThat(list).extracting(CommentItem::content, CommentItem::isMine)
                .contains(tuple("내 댓글", true));
    }

    @Test
    @DisplayName("replyCount 는 노출되는 답글만 센다 — 답글 자체는 목록에 없다")
    void countsOnlyVisibleReplies() {
        Long root = data.challengeComment(challengeSeq).author(fan).content("루트")
                .at(base.minusSeconds(10)).insert();
        Integer leaver = videos.newUser();

        data.challengeComment(challengeSeq).author(me).content("답글1").replyTo(root).insert();
        data.challengeComment(challengeSeq).author(fan).content("답글2").replyTo(root).insert();
        data.challengeComment(challengeSeq).author(fan).content("삭제된 답글").replyTo(root)
                .deleted().insert();
        data.challengeComment(challengeSeq).author(leaver).content("탈퇴자 답글").replyTo(root)
                .insert();
        data.deleteUser(leaver);

        PageResponse<CommentItem> page = commentQueryService
                .challengeComments(me, challengeSeq, 0, 20);

        assertThat(page.list()).extracting(CommentItem::content).containsExactly("루트");
        assertThat(page.list().getFirst().replyCount()).isEqualTo(2);
        assertThat(page.totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("isLiked·likeCount 는 내 좋아요 기준 — 남의 좋아요는 isLiked 가 아니다")
    void marksMyLikes() {
        Long liked = data.challengeComment(challengeSeq).author(fan).content("좋아요한 댓글")
                .likes(3).at(base.minusSeconds(20)).insert();
        data.challengeComment(challengeSeq).author(fan).content("안 누른 댓글")
                .likes(1).at(base.minusSeconds(10)).insert();
        data.likeChallengeComment(liked, me);

        List<CommentItem> list = commentQueryService
                .challengeComments(me, challengeSeq, 0, 20).list();

        assertThat(list).extracting(CommentItem::content, CommentItem::isLiked,
                        CommentItem::likeCount)
                .containsExactly(
                        tuple("안 누른 댓글", false, 1),
                        tuple("좋아요한 댓글", true, 3));
    }

    @Test
    @DisplayName("삭제된 댓글과 탈퇴 사용자의 댓글은 목록에 없다 (§1.8)")
    void hidesDeletedRows() {
        Integer leaver = videos.newUser();
        data.challengeComment(challengeSeq).author(fan).content("보이는 댓글")
                .at(base.minusSeconds(30)).insert();
        data.challengeComment(challengeSeq).author(fan).content("삭제된 댓글")
                .deleted().at(base.minusSeconds(20)).insert();
        data.challengeComment(challengeSeq).author(leaver).content("탈퇴자 댓글")
                .at(base.minusSeconds(10)).insert();
        data.deleteUser(leaver);

        PageResponse<CommentItem> page = commentQueryService
                .challengeComments(me, challengeSeq, 0, 20);

        assertThat(page.list()).extracting(CommentItem::content).containsExactly("보이는 댓글");
        assertThat(page.totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 시각 댓글도 페이지 경계에서 겹치거나 빠지지 않는다")
    void pagesAreStableOnTiedTimestamps() {
        for (int i = 1; i <= 5; i++) {
            data.challengeComment(challengeSeq).author(fan).content("댓글" + i).at(base).insert();
        }

        List<Long> seen = List.of(0, 1, 2).stream()
                .map(p -> commentQueryService.challengeComments(me, challengeSeq, p, 2))
                .flatMap(page -> page.list().stream())
                .map(CommentItem::commentSeq)
                .toList();

        assertThat(seen).doesNotHaveDuplicates().hasSize(5);
        PageResponse<CommentItem> first = commentQueryService
                .challengeComments(me, challengeSeq, 0, 2);
        assertThat(first.totalElements()).isEqualTo(5);
        assertThat(first.totalPages()).isEqualTo(3);
        assertThat(first.hasNext()).isTrue();
    }

    @Test
    @DisplayName("참여 영상 — 댓글 잠금은 목록 조회까지 403")
    void videoCommentsBlockedWhenDisabled() {
        Long videoSeq = videos.video(challengeSeq, artistSeq).owner(me).insert();
        data.videoComment(videoSeq).author(fan).content("영상 댓글").insert();
        data.disableComments(videoSeq);

        assertThatThrownBy(() -> commentQueryService.videoComments(me, videoSeq, 0, 20))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommentErrorCode.COMMENT_DISABLED);
    }

    @Test
    @DisplayName("참여 영상 — 타인의 PRIVATE 은 404 다 (403 이 아니다)")
    void otherPrivateVideoIsNotFound() {
        Long videoSeq = videos.video(challengeSeq, artistSeq).owner(fan)
                .privateVisibility().insert();

        assertThatThrownBy(() -> commentQueryService.videoComments(me, videoSeq, 0, 20))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(VideoErrorCode.VIDEO_NOT_FOUND);
    }

    @Test
    @DisplayName("참여 영상 — 영상의 아티스트 댓글이 1순위다")
    void videoCommentsSortArtistFirst() {
        Long videoSeq = videos.video(challengeSeq, artistSeq).owner(me).insert();
        data.videoComment(videoSeq).author(fan).content("일반").at(base.minusSeconds(5)).insert();
        data.videoComment(videoSeq).author(artistUserSeq).content("아티스트")
                .at(base.minusSeconds(50)).insert();

        List<CommentItem> list = commentQueryService.videoComments(me, videoSeq, 0, 20).list();

        assertThat(list).extracting(CommentItem::content).containsExactly("아티스트", "일반");
        assertThat(list.getFirst().author().isArtist()).isTrue();
    }

    @Test
    @DisplayName("공지 — 목록이 나오고, 삭제된 공지는 404")
    void noticeComments() {
        Integer noticeSeq = data.newNotice(artistSeq);
        data.noticeComment(noticeSeq).author(artistUserSeq).content("아티스트")
                .at(base.minusSeconds(50)).insert();
        data.noticeComment(noticeSeq).author(fan).content("일반").at(base.minusSeconds(5)).insert();

        PageResponse<CommentItem> page = commentQueryService.noticeComments(me, noticeSeq, 0, 20);
        assertThat(page.list()).extracting(CommentItem::content).containsExactly("아티스트", "일반");

        entityManager.createNativeQuery(
                        "update motif_core.notices set is_deleted = true where notice_seq = ?1")
                .setParameter(1, noticeSeq)
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();

        assertThatThrownBy(() -> commentQueryService.noticeComments(me, noticeSeq, 0, 20))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(NoticeErrorCode.NOTICE_NOT_FOUND);
    }

    @Test
    @DisplayName("대상이 없으면 404 — 미존재·삭제된 챌린지")
    void missingTargetsAreNotFound() {
        Integer deletedChallenge = videos.newChallenge(artistSeq, videos.newSong(me), true);

        assertThatThrownBy(() -> commentQueryService.challengeComments(me, deletedChallenge, 0, 20))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ChallengeErrorCode.CHALLENGE_NOT_FOUND);

        assertThatThrownBy(() -> commentQueryService.challengeComments(me, -1, 0, 20))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ChallengeErrorCode.CHALLENGE_NOT_FOUND);

        assertThatThrownBy(() -> commentQueryService.noticeComments(me, -1, 0, 20))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(NoticeErrorCode.NOTICE_NOT_FOUND);

        assertThatThrownBy(() -> commentQueryService.videoComments(me, -1L, 0, 20))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(VideoErrorCode.VIDEO_NOT_FOUND);
    }

    @Test
    @DisplayName("댓글이 없으면 빈 페이지다 — 추가 질의를 하지 않는다")
    void emptyPage() {
        PageResponse<CommentItem> page = commentQueryService
                .challengeComments(me, challengeSeq, 0, 20);

        assertThat(page.list()).isEmpty();
        assertThat(page.totalElements()).isZero();
        assertThat(page.hasNext()).isFalse();
    }
}
