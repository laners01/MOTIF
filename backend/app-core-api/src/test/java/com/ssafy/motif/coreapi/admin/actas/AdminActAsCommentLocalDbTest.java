package com.ssafy.motif.coreapi.admin.actas;

import com.ssafy.motif.common.error.CommonErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 댓글 대행 (어드민 명세 §3.12) 를 실제 DB에 대고 검증한다 (S15P21D104-409).
 *
 * <p>대행 컨트롤러에는 규칙이 없으므로 여기서 볼 것은 <b>액터 치환이 실제로 먹혔는가</b>
 * 하나다 — 댓글 행의 주인이 어드민이 아니라 아티스트여야 하고, 그 결과 §8.2 의
 * {@code has_artist_comment} 가 대행만으로 서야 한다. 이게 어긋나면 화면에는 아티스트
 * 댓글이 달린 것처럼 보이는데 배지는 영영 붙지 않는다.
 *
 * <p>삭제 403 은 대행이 <b>넓혀주지 않는</b> 권한을 확인한다 — 타인 댓글은 신고 처리
 * (§3.13)로 지우는 것이지 대행으로 지우는 것이 아니다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class AdminActAsCommentLocalDbTest {

    @Autowired
    private AdminActAsCommentController controller;
    @Autowired
    private ActAsArtistResolver actAsArtistResolver;
    @Autowired
    private CommentCommandService commentCommandService;
    @PersistenceContext
    private EntityManager entityManager;

    private VideoTestData data;
    private Integer artistSeq;
    private Long videoSeq;

    @BeforeEach
    void setUp() {
        data = new VideoTestData(entityManager);
        artistSeq = data.newArtist();
        Integer challengeSeq = data.newChallenge(artistSeq, data.newSong(data.newUser()), false);
        videoSeq = data.video(challengeSeq, artistSeq).owner(data.newUser()).insert();
    }

    @Test
    @DisplayName("대행 작성은 아티스트 이름으로 남고 has_artist_comment 를 세운다")
    void writeOnVideoActsAsArtist() {
        Long commentSeq = controller.writeOnVideo(artistSeq, videoSeq,
                new CommentCreateRequest("아티스트가 답니다", null)).data().commentSeq();
        entityManager.flush();

        assertThat(commentOwner(commentSeq))
                .isEqualTo(actAsArtistResolver.actorUserSeq(artistSeq));
        assertThat(hasArtistComment()).isTrue();
    }

    @Test
    @DisplayName("대행 삭제는 아티스트 본인 댓글만 — 타인 댓글은 403")
    void deleteOnlyOwnComment() {
        Long mine = controller.writeOnVideo(artistSeq, videoSeq,
                new CommentCreateRequest("아티스트 댓글", null)).data().commentSeq();
        Long others = commentCommandService.writeOnVideo(data.newUser(), videoSeq,
                new CommentCreateRequest("팬 댓글", null)).commentSeq();
        entityManager.flush();

        assertThatThrownBy(() -> controller.delete(artistSeq, "video", others))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.FORBIDDEN);

        assertThat(controller.delete(artistSeq, "video", mine).data().deletedCount()).isEqualTo(1);
        entityManager.flush();
        assertThat(hasArtistComment()).isFalse();
    }

    private Integer commentOwner(Long commentSeq) {
        return ((Number) entityManager.createNativeQuery(
                        "select user_seq from motif_core.video_comments where video_comment_seq = ?1")
                .setParameter(1, commentSeq)
                .getSingleResult()).intValue();
    }

    private boolean hasArtistComment() {
        entityManager.clear();
        return (Boolean) entityManager.createNativeQuery(
                        "select has_artist_comment from motif_core.participation_videos where video_seq = ?1")
                .setParameter(1, videoSeq)
                .getSingleResult();
    }
}
