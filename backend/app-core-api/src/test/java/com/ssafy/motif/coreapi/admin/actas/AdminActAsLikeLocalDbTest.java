package com.ssafy.motif.coreapi.admin.actas;

import com.ssafy.motif.coreapi.admin.security.AuthenticatedAdmin;
import com.ssafy.motif.coreapi.domain.comment.dto.CommentCreateRequest;
import com.ssafy.motif.coreapi.domain.comment.service.CommentCommandService;
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

/**
 * 영상 좋아요 대행 (어드민 명세 §3.12 · ADMIN-11) — S15P21D104-408 · 573.
 *
 * <p>대행의 값어치가 여기 있다 — 어드민이 눌렀는데 <b>아티스트가 누른 것으로</b> 기록돼
 * {@code has_artist_like} 가 서야 한다. 액터 치환이 빠지면 어드민 계정의 좋아요가 되어
 * 카운터만 오르고 플래그는 영영 서지 않는데, 둘 다 예외 없이 지나간다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class AdminActAsLikeLocalDbTest {

    @Autowired
    private AdminActAsLikeController controller;
    @Autowired
    private ActAsArtistResolver actAsArtistResolver;
    @Autowired
    private CommentCommandService commentCommandService;
    @PersistenceContext
    private EntityManager entityManager;

    private VideoTestData data;
    private AuthenticatedAdmin admin;
    private Integer artistSeq;
    private Long videoSeq;

    @BeforeEach
    void setUp() {
        data = new VideoTestData(entityManager);
        admin = new AuthenticatedAdmin(1, data.newUser(), "운영자");
        artistSeq = data.newArtist();
        Integer challengeSeq = data.newChallenge(artistSeq, data.newSong(data.newUser()), false);
        videoSeq = data.video(challengeSeq, artistSeq).owner(data.newUser()).insert();
    }

    @Test
    @DisplayName("대행 좋아요는 아티스트 이름으로 남고 has_artist_like 를 세운다")
    void likeActsAsArtist() {
        controller.likeVideo(admin, artistSeq, videoSeq);
        entityManager.flush();

        assertThat(likeOwner()).isEqualTo(actAsArtistResolver.actorUserSeq(artistSeq));
        assertThat(likeOwner()).isNotEqualTo(admin.userSeq());
        assertThat(flag()).isTrue();
        assertThat(count()).isEqualTo(1);
    }

    @Test
    @DisplayName("해제하면 플래그와 카운터가 함께 내려간다")
    void unlikeClearsFlag() {
        controller.likeVideo(admin, artistSeq, videoSeq);
        controller.unlikeVideo(admin, artistSeq, videoSeq);
        entityManager.flush();

        assertThat(flag()).isFalse();
        assertThat(count()).isZero();
    }

    @Test
    @DisplayName("댓글 좋아요 대행도 아티스트 이름으로 남는다 — 카운터가 오른다")
    void likeCommentActsAsArtist() {
        Integer fan = data.newUser();
        Long commentSeq = commentCommandService
                .writeOnVideo(fan, videoSeq, new CommentCreateRequest("팬 댓글", null)).commentSeq();

        controller.likeComment(admin, artistSeq, "video", commentSeq);
        entityManager.flush();
        entityManager.clear();

        assertThat(commentLikeCount(commentSeq)).isEqualTo(1);
        assertThat(commentLikeOwner(commentSeq))
                .isEqualTo(actAsArtistResolver.actorUserSeq(artistSeq));
    }

    private int commentLikeCount(Long commentSeq) {
        return ((Number) entityManager.createNativeQuery(
                        "select like_count from motif_core.video_comments where video_comment_seq = ?1")
                .setParameter(1, commentSeq)
                .getSingleResult()).intValue();
    }

    private Integer commentLikeOwner(Long commentSeq) {
        return ((Number) entityManager.createNativeQuery(
                        "select user_seq from motif_core.video_comment_likes where video_comment_seq = ?1")
                .setParameter(1, commentSeq)
                .getSingleResult()).intValue();
    }

    @Test
    @DisplayName("챌린지 좋아요 대행도 아티스트 이름으로 남는다")
    void likeChallengeActsAsArtist() {
        Integer challengeSeq = data.newChallenge(artistSeq, data.newSong(data.newUser()), false);

        controller.likeChallenge(admin, artistSeq, challengeSeq);
        entityManager.flush();
        entityManager.clear();

        assertThat(challengeLikeOwner(challengeSeq))
                .isEqualTo(actAsArtistResolver.actorUserSeq(artistSeq));
        assertThat(challengeLikeCount(challengeSeq)).isEqualTo(1);
    }

    private Integer challengeLikeOwner(Integer challengeSeq) {
        return ((Number) entityManager.createNativeQuery(
                        "select user_seq from motif_core.challenge_likes where challenge_seq = ?1")
                .setParameter(1, challengeSeq)
                .getSingleResult()).intValue();
    }

    private int challengeLikeCount(Integer challengeSeq) {
        return ((Number) entityManager.createNativeQuery(
                        "select like_count from motif_core.challenges where challenge_seq = ?1")
                .setParameter(1, challengeSeq)
                .getSingleResult()).intValue();
    }

    private Integer likeOwner() {
        return ((Number) entityManager.createNativeQuery(
                        "select user_seq from motif_core.video_likes where video_seq = ?1")
                .setParameter(1, videoSeq)
                .getSingleResult()).intValue();
    }

    private boolean flag() {
        entityManager.clear();
        return (Boolean) entityManager.createNativeQuery(
                        "select has_artist_like from motif_core.participation_videos where video_seq = ?1")
                .setParameter(1, videoSeq)
                .getSingleResult();
    }

    private int count() {
        entityManager.clear();
        return ((Number) entityManager.createNativeQuery(
                        "select like_count from motif_core.participation_videos where video_seq = ?1")
                .setParameter(1, videoSeq)
                .getSingleResult()).intValue();
    }
}
