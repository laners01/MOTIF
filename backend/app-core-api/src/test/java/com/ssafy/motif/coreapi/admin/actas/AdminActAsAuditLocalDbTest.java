package com.ssafy.motif.coreapi.admin.actas;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ssafy.motif.coreapi.admin.security.AuthenticatedAdmin;
import com.ssafy.motif.coreapi.domain.comment.dto.CommentCreateRequest;
import com.ssafy.motif.coreapi.domain.video.VideoTestData;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 대행 감사 로그 (어드민 명세 §1.3) 를 실제 DB에 대고 검증한다 (S15P21D104-574).
 *
 * <p>이 로그가 <b>유일한 기록</b>이라 테스트가 필요하다 — 댓글 행에는 어드민을 남길 칸이
 * 없어서(§1.3), 호출이 빠져도 DB 어디에도 흔적이 없고 화면도 멀쩡히 돈다. 한참 뒤
 * "누가 이 댓글을 달았나" 를 물었을 때에야 비어 있는 것이 드러난다.
 *
 * <p>MDC 필드까지 보는 것은 JSON 파일 appender 가 그 필드를 뽑아 쓰기 때문이다 —
 * 메시지 문자열만 맞고 필드가 비면 파일에는 빈 칸이 남는다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class AdminActAsAuditLocalDbTest {

    @Autowired
    private AdminActAsCommentController controller;
    @PersistenceContext
    private EntityManager entityManager;

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Logger auditLogger;

    private AuthenticatedAdmin admin;
    private Integer artistSeq;
    private Integer challengeSeq;
    private Long videoSeq;

    @BeforeEach
    void setUp() {
        VideoTestData data = new VideoTestData(entityManager);
        admin = new AuthenticatedAdmin(42, data.newUser(), "운영자");
        artistSeq = data.newArtist();
        challengeSeq = data.newChallenge(artistSeq, data.newSong(data.newUser()), false);
        videoSeq = data.video(challengeSeq, artistSeq).owner(data.newUser()).insert();

        auditLogger = (Logger) LoggerFactory.getLogger("motif.admin.audit");
        appender.start();
        auditLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        auditLogger.detachAppender(appender);
        appender.stop();
    }

    @Test
    @DisplayName("대행 작성·답글·삭제가 각각 ADMIN_ACT_AS 한 줄을 남긴다")
    void writesOneLinePerAction() {
        Long root = controller.writeOnVideo(admin, artistSeq, videoSeq,
                new CommentCreateRequest("댓글", null)).data().commentSeq();
        controller.writeOnVideo(admin, artistSeq, videoSeq,
                new CommentCreateRequest("답글", root));
        controller.delete(admin, artistSeq, "video", root);

        assertThat(actions()).containsExactly(
                "CREATE_COMMENT_VIDEO", "CREATE_REPLY_VIDEO", "DELETE_COMMENT");
    }

    @Test
    @DisplayName("로그에 수행 어드민과 대행 아티스트가 함께 실린다 — 둘 다 없으면 추적이 끊긴다")
    void carriesAdminAndArtist() {
        Long commentSeq = controller.writeOnVideo(admin, artistSeq, videoSeq,
                new CommentCreateRequest("댓글", null)).data().commentSeq();

        assertThat(events()).singleElement().satisfies(event -> {
            assertThat(event.getFormattedMessage()).startsWith("ADMIN_ACT_AS");
            Map<String, String> mdc = event.getMDCPropertyMap();
            assertThat(mdc).containsEntry("adminSeq", "42")
                    .containsEntry("userSeq", String.valueOf(admin.userSeq()))
                    .containsEntry("artistSeq", String.valueOf(artistSeq))
                    .containsEntry("targetSeq", String.valueOf(commentSeq))
                    .containsEntry("result", "OK");
        });
    }

    @Test
    @DisplayName("조회 대행은 아무것도 남기지 않는다 — 감사 대상은 쓰기다(§1.3)")
    void readsAreNotAudited() {
        controller.videoComments(artistSeq, videoSeq, 0, 20);
        controller.challengeComments(artistSeq, challengeSeq, 0, 20);

        assertThat(events()).isEmpty();
    }

    private List<ILoggingEvent> events() {
        return appender.list;
    }

    private List<String> actions() {
        return events().stream().map(e -> e.getMDCPropertyMap().get("action")).toList();
    }
}
