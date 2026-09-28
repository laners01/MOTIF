package com.ssafy.motif.coreapi.admin.report;

import com.ssafy.motif.common.error.AdminErrorCode;
import com.ssafy.motif.common.error.CommonErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.admin.report.dto.ReportProcessRequest;
import com.ssafy.motif.coreapi.admin.report.dto.ReportProcessResponse;
import com.ssafy.motif.coreapi.admin.security.AuthenticatedAdmin;
import com.ssafy.motif.coreapi.domain.video.VideoTestData;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * §3.13.3 인용/기각을 실제 DB에 대고 검증한다 (S15P21D104-579).
 *
 * <p><b>보려는 것은 멱등이다.</b> 이미 삭제된 대상을 인용해도 {@code participant_count}
 * 가 한 번만 내려가야 한다. 두 번 내려가면 챌린지 목록의 참여 수가 영영 틀어지는데,
 * 예외도 로그도 없이 숫자만 어긋난다 — 인메모리로는 잡히지 않는다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class AdminReportProcessLocalDbTest {

    private static final ReportProcessRequest ACCEPT =
            new ReportProcessRequest(ReportStatus.ACCEPTED);
    private static final ReportProcessRequest REJECT =
            new ReportProcessRequest(ReportStatus.REJECTED);

    @Autowired
    private AdminReportService adminReportService;
    @Autowired
    private ReportRepository reportRepository;
    @PersistenceContext
    private EntityManager entityManager;

    private VideoTestData data;
    /** 감사 컬럼(processed_usr_seq·mod_usr_seq)이 users FK 라 실제 행이 필요하다. */
    private AuthenticatedAdmin admin;
    private Integer challengeSeq;
    private Integer reporter;
    private Long videoSeq;

    @BeforeEach
    void setUp() {
        data = new VideoTestData(entityManager);
        Integer artistSeq = data.newArtist();
        admin = new AuthenticatedAdmin(1, data.newUser(), "운영자");
        reporter = data.newUser();
        // newSong 의 인자는 등록자(user_seq)다 — artistSeq 를 넣으면 users FK 가 깨진다
        challengeSeq = data.newChallenge(artistSeq, data.newSong(reporter), false);
        videoSeq = data.video(challengeSeq, artistSeq).owner(data.newUser()).insert();
        setParticipantCount(1);
    }

    @Test
    @DisplayName("인용 — 대상을 지우고 participant_count 를 한 번 내린다")
    void acceptDeletesTargetAndDecrements() {
        ReportProcessResponse result = adminReportService.process(admin, newReport(reporter), ACCEPT);

        assertThat(result.status()).isEqualTo(ReportStatus.ACCEPTED);
        assertThat(result.targetDeleted()).isTrue();
        assertThat(result.sideEffects().participantCountDelta()).isEqualTo(-1);
        assertThat(videoDeleted()).isTrue();
        assertThat(participantCount()).isZero();
    }

    @Test
    @DisplayName("인용 — 이미 삭제된 대상이면 카운터를 다시 내리지 않는다 (멱등, DB §26)")
    void acceptOnAlreadyDeletedTargetIsIdempotent() {
        adminReportService.process(admin, newReport(reporter), ACCEPT);
        assertThat(participantCount()).isZero();

        // 다른 사람이 같은 영상을 신고했고 그것도 인용한다
        ReportProcessResponse result =
                adminReportService.process(admin, newReport(data.newUser()), ACCEPT);

        assertThat(result.targetDeleted()).isTrue();
        // 여기가 이 테스트의 전부다 — 두 번 내려가면 음수가 되거나 다른 영상 몫까지 깎인다
        assertThat(result.sideEffects().participantCountDelta()).isZero();
        assertThat(participantCount()).isZero();
    }

    @Test
    @DisplayName("기각 — 대상을 지우지 않는다")
    void rejectKeepsTarget() {
        ReportProcessResponse result = adminReportService.process(admin, newReport(reporter), REJECT);

        assertThat(result.status()).isEqualTo(ReportStatus.REJECTED);
        assertThat(result.targetDeleted()).isFalse();
        assertThat(videoDeleted()).isFalse();
        assertThat(participantCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("이미 처리된 신고는 409")
    void alreadyProcessed() {
        long reportSeq = newReport(reporter);
        adminReportService.process(admin, reportSeq, REJECT);

        assertThatThrownBy(() -> adminReportService.process(admin, reportSeq, ACCEPT))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AdminErrorCode.REPORT_ALREADY_PROCESSED);
    }

    @Test
    @DisplayName("decision 이 RECEIVED 면 400 — 접수로 되돌리는 전이는 없다")
    void receivedIsNotADecision() {
        long reportSeq = newReport(reporter);

        assertThatThrownBy(() -> adminReportService.process(admin, reportSeq,
                new ReportProcessRequest(ReportStatus.RECEIVED)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(CommonErrorCode.INVALID_PARAMETER);
    }

    // ── 도우미 ────────────────────────────────────────────────────

    private long newReport(Integer reporterSeq) {
        reportRepository.insertVideoReport(reporterSeq, videoSeq, "SPAM");
        entityManager.flush();
        return reportRepository.findByReporterSeqAndVideoSeq(reporterSeq, videoSeq)
                .orElseThrow().getReportSeq();
    }

    private void setParticipantCount(int count) {
        entityManager.createNativeQuery(
                        "update motif_core.challenges set participant_count = ?1 where challenge_seq = ?2")
                .setParameter(1, count).setParameter(2, challengeSeq).executeUpdate();
        entityManager.flush();
        entityManager.clear();
    }

    private int participantCount() {
        entityManager.flush();
        return ((Number) entityManager.createNativeQuery(
                        "select participant_count from motif_core.challenges where challenge_seq = ?1")
                .setParameter(1, challengeSeq).getSingleResult()).intValue();
    }

    private boolean videoDeleted() {
        entityManager.flush();
        return (Boolean) entityManager.createNativeQuery(
                        "select is_deleted from motif_core.participation_videos where video_seq = ?1")
                .setParameter(1, videoSeq).getSingleResult();
    }
}
