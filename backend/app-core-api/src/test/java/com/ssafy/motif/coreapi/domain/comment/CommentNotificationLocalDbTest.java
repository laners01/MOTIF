package com.ssafy.motif.coreapi.domain.comment;

import com.ssafy.motif.coreapi.domain.comment.dto.CommentCreateRequest;
import com.ssafy.motif.coreapi.domain.comment.service.CommentCommandService;
import com.ssafy.motif.coreapi.domain.video.VideoTestData;
import com.ssafy.motif.event.contract.CommentCreatedEvent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 댓글 알림 이벤트의 수신자 판정 (API §9.3 · ADMIN-11) — S15P21D104-315·321.
 *
 * <p>여기서 보는 것은 <b>누구에게 가는가</b> 하나다. 발행 자체는 한 줄이고, 틀리기 쉬운
 * 것은 수신자다 — 특히 참여 영상은 "게시물 소유자" 가 올린 사람이지 그 챌린지의
 * 아티스트가 아니다. 서비스가 이미 들고 있는 {@code artistUserSeq} 를 그대로 쓰면
 * 엉뚱한 사람에게 가는데, 둘 다 값이 있어서 실행해도 예외가 나지 않는다.
 *
 * <p>커밋 후 relay 는 여기서 돌지 않는다({@code @Transactional} 이라 커밋이 없다) —
 * 발행된 애플리케이션 이벤트를 보는 것으로 충분하다. MQ 로 나가는 부분은 relay 의
 * 책임이고 챌린지·공지 relay 와 같은 모양이다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
@RecordApplicationEvents
class CommentNotificationLocalDbTest {

    @Autowired
    private CommentCommandService commentCommandService;
    @Autowired
    private ApplicationEvents events;
    @PersistenceContext
    private EntityManager entityManager;

    private VideoTestData data;
    private Integer artistSeq;
    private Integer owner;
    private Long videoSeq;

    @BeforeEach
    void setUp() {
        data = new VideoTestData(entityManager);
        artistSeq = data.newArtist();
        Integer challengeSeq = data.newChallenge(artistSeq, data.newSong(data.newUser()), false);
        owner = data.newUser();
        videoSeq = data.video(challengeSeq, artistSeq).owner(owner).insert();
    }

    @Test
    @DisplayName("영상 댓글은 아티스트가 아니라 올린 사람에게 간다")
    void videoCommentGoesToUploader() {
        Integer fan = data.newUser();

        commentCommandService.writeOnVideo(fan, videoSeq, new CommentCreateRequest("좋아요", null));

        assertThat(published()).singleElement().satisfies(event -> {
            assertThat(event.recipientUserSeq()).isEqualTo(owner);
            assertThat(event.recipientUserSeq()).isNotEqualTo(artistUserSeq());
            assertThat(event.targetType()).isEqualTo("VIDEO");
            assertThat(event.targetSeq()).isEqualTo(videoSeq);
            assertThat(event.isReply()).isFalse();
        });
    }

    @Test
    @DisplayName("답글은 게시물 소유자가 아니라 부모 댓글 작성자에게 간다")
    void replyGoesToParentAuthor() {
        Integer first = data.newUser();
        Integer second = data.newUser();
        Long root = commentCommandService
                .writeOnVideo(first, videoSeq, new CommentCreateRequest("첫 댓글", null)).commentSeq();

        commentCommandService.writeOnVideo(second, videoSeq, new CommentCreateRequest("답글", root));

        assertThat(published()).hasSize(2);
        assertThat(published().get(1)).satisfies(event -> {
            assertThat(event.recipientUserSeq()).isEqualTo(first);
            assertThat(event.recipientUserSeq()).isNotEqualTo(owner);
            assertThat(event.isReply()).isTrue();
            assertThat(event.parentCommentSeq()).isEqualTo(root);
        });
    }

    @Test
    @DisplayName("자기 게시물에 자기가 달면 발행하지 않는다 — 대행에서 아티스트가 자기 공지에 다는 경우가 이것이다")
    void selfCommentPublishesNothing() {
        commentCommandService.writeOnVideo(owner, videoSeq, new CommentCreateRequest("내 영상", null));

        assertThat(published()).isEmpty();
    }

    private List<CommentCreatedEvent> published() {
        return events.stream(CommentCreatedEvent.class).toList();
    }

    private Integer artistUserSeq() {
        return ((Number) entityManager.createNativeQuery(
                        "select user_seq from motif_core.artists where artist_seq = ?1")
                .setParameter(1, artistSeq)
                .getSingleResult()).intValue();
    }
}
