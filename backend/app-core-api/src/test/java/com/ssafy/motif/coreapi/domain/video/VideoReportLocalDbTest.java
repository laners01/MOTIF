package com.ssafy.motif.coreapi.domain.video;

import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.domain.report.ReportErrorCode;
import com.ssafy.motif.coreapi.domain.video.service.VideoQueryService;
import com.ssafy.motif.coreapi.domain.video.service.VideoReportService;
import com.ssafy.motif.domain.core.report.Report;
import com.ssafy.motif.domain.core.report.ReportRepository;
import com.ssafy.motif.domain.core.report.ReportStatus;
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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * §8.7 영상 신고를 실제 DB에 대고 검증한다 (S15P21D104-160).
 *
 * <p><b>DB 제약이 곧 규칙이라 단위 테스트가 닿지 않는다.</b> 409 는 애플리케이션 분기가
 * 아니라 {@code uq_reports_reporter_video} 와 {@code on conflict do nothing} 의 영향
 * 행 수에서 나오고, 4대상 XOR CHECK 도 INSERT 가 실제로 돌아야 확인된다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class VideoReportLocalDbTest {

    @Autowired
    private VideoReportService videoReportService;
    @Autowired
    private VideoQueryService videoQueryService;
    @Autowired
    private ReportRepository reportRepository;
    @PersistenceContext
    private EntityManager entityManager;

    private VideoTestData data;
    private Integer me;
    private Integer other;
    private Integer artistSeq;
    private Integer challengeSeq;

    @BeforeEach
    void setUp() {
        data = new VideoTestData(entityManager);
        me = data.newUser();
        other = data.newUser();
        artistSeq = data.newArtist();
        challengeSeq = data.newChallenge(artistSeq, data.newSong(me), false);
    }

    // --- 접수 ---------------------------------------------------------------

    @Test
    @DisplayName("접수되면 RECEIVED 로 행이 생긴다 — 처리자·처리 시각은 비어 있다")
    void reportIsReceived() {
        Long 영상 = video().owner(other).insert();

        videoReportService.report(me, 영상, "SPAM");

        Report report = reportRepository.findByReporterSeqAndVideoSeq(me, 영상).orElseThrow();
        assertThat(report.getReasonCode()).isEqualTo("SPAM");
        assertThat(report.getStatus()).isEqualTo(ReportStatus.RECEIVED);
        assertThat(report.getProcessedUsrSeq()).isNull();
        assertThat(report.getProcessedDttm()).isNull();
        assertThat(report.getRegDttm()).isNotNull();
        // 4대상 XOR — 영상 칸만 채워진다
        assertThat(report.getVideoSeq()).isEqualTo(영상);
        assertThat(report.getChallengeCommentSeq()).isNull();
        assertThat(report.getVideoCommentSeq()).isNull();
        assertThat(report.getNoticeCommentSeq()).isNull();
    }

    @Test
    @DisplayName("코드표에 없는 값도 그대로 저장된다 — reasonCode 는 문자열이다 (§16 #4 미결)")
    void reasonCodeIsFreeText() {
        Long 영상 = video().owner(other).insert();

        videoReportService.report(me, 영상, "저작권_침해");

        assertThat(reportRepository.findByReporterSeqAndVideoSeq(me, 영상).orElseThrow()
                .getReasonCode()).isEqualTo("저작권_침해");
    }

    @Test
    @DisplayName("신고해도 영상은 그대로 보인다 — 접수는 제재가 아니다")
    void reportDoesNotHideTheVideo() {
        Long 영상 = video().owner(other).insert();

        videoReportService.report(me, 영상, "SPAM");

        assertThat(videoQueryService.detail(me, 영상).videoSeq()).isEqualTo(영상);
    }

    @Test
    @DisplayName("본인 영상도 신고할 수 있다 — 막을 근거가 명세에 없다")
    void canReportOwnVideo() {
        Long 내영상 = video().owner(me).insert();

        assertThatCode(() -> videoReportService.report(me, 내영상, "SPAM"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("다른 사용자는 같은 영상을 각자 신고할 수 있다 — 1인 1대상이지 1대상 1건이 아니다")
    void differentReportersMayReportTheSameVideo() {
        Long 영상 = video().owner(other).insert();
        Integer 제삼자 = data.newUser();

        videoReportService.report(me, 영상, "SPAM");

        assertThatCode(() -> videoReportService.report(제삼자, 영상, "SPAM"))
                .doesNotThrowAnyException();
    }

    // --- 409 ----------------------------------------------------------------

    @Test
    @DisplayName("같은 대상을 다시 신고하면 409 다 — 사유가 달라도 그렇다")
    void reportingTwiceIs409() {
        Long 영상 = video().owner(other).insert();
        videoReportService.report(me, 영상, "SPAM");

        assertThatThrownBy(() -> videoReportService.report(me, 영상, "ABUSE"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ReportErrorCode.ALREADY_REPORTED);
    }

    // --- 404 (§8.2 와 같은 규칙) ----------------------------------------------

    @Test
    @DisplayName("없는 영상은 404 다")
    void unknownVideoIs404() {
        assertNotFound(9_999_999_999L);
    }

    @Test
    @DisplayName("삭제된 영상은 404 다")
    void deletedVideoIs404() {
        assertNotFound(video().owner(other).deleted().insert());
    }

    @Test
    @DisplayName("삭제된 사용자의 영상은 404 다")
    void videoOfDeletedUserIs404() {
        Integer 탈퇴자 = data.newUser();
        Long 탈퇴자영상 = video().owner(탈퇴자).insert();
        data.execute("update motif_core.users set is_deleted = true where user_seq = ?1", 탈퇴자);

        assertNotFound(탈퇴자영상);
    }

    @Test
    @DisplayName("삭제된 챌린지에 속한 영상은 404 다")
    void videoOfDeletedChallengeIs404() {
        Integer 삭제된챌린지 = data.newChallenge(artistSeq, data.newSong(me), true);

        assertNotFound(data.video(삭제된챌린지, artistSeq).owner(other).insert());
    }

    @Test
    @DisplayName("타인의 PRIVATE 은 404 다 — 신고만 느슨하면 존재 은닉이 뚫린다")
    void othersPrivateVideoIs404() {
        Long 남의비공개 = video().owner(other).privateVisibility().insert();

        assertNotFound(남의비공개);
        assertThat(reportRepository.findByReporterSeqAndVideoSeq(me, 남의비공개)).isEmpty();
    }

    // --- 도우미 -------------------------------------------------------------

    private void assertNotFound(Long videoSeq) {
        assertThatThrownBy(() -> videoReportService.report(me, videoSeq, "SPAM"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(VideoErrorCode.VIDEO_NOT_FOUND);
    }

    private VideoTestData.VideoFixture video() {
        return data.video(challengeSeq, artistSeq);
    }
}
