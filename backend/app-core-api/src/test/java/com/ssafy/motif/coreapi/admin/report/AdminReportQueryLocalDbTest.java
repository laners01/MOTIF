package com.ssafy.motif.coreapi.admin.report;

import com.ssafy.motif.common.error.AdminErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.admin.report.dto.ReportItem;
import com.ssafy.motif.coreapi.domain.video.VideoTestData;
import com.ssafy.motif.domain.core.report.ReportRepository;
import com.ssafy.motif.domain.core.report.ReportStatus;
import com.ssafy.motif.domain.core.report.ReportTargetType;
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
 * §3.13.1·§3.13.2 신고 목록·상세를 실제 DB에 대고 검증한다 (S15P21D104-578).
 *
 * <p>대상 미리보기가 §3.12 조회를 그대로 타는지, 같은 대상에 쌓인 신고 수가 맞는지를 본다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class AdminReportQueryLocalDbTest {

    @Autowired
    private AdminReportService adminReportService;
    @Autowired
    private ReportRepository reportRepository;
    @PersistenceContext
    private EntityManager entityManager;

    private VideoTestData data;
    private Integer reporter;
    private Long videoSeq;

    @BeforeEach
    void setUp() {
        data = new VideoTestData(entityManager);
        Integer artistSeq = data.newArtist();
        reporter = data.newUser();
        // newSong 의 인자는 등록자(user_seq)다 — artistSeq 를 넣으면 users FK 가 깨진다
        Integer challengeSeq = data.newChallenge(artistSeq, data.newSong(reporter), false);
        videoSeq = data.video(challengeSeq, artistSeq).owner(data.newUser()).insert();
    }

    @Test
    @DisplayName("상세 — 영상 대상이 미리보기와 함께 온다")
    void detail() {
        long reportSeq = newReport(reporter);

        ReportItem item = adminReportService.detail(reportSeq);

        assertThat(item.targetType()).isEqualTo(ReportTargetType.VIDEO);
        assertThat(item.status()).isEqualTo(ReportStatus.RECEIVED);
        assertThat(item.reporter().userSeq()).isEqualTo(reporter);
        assertThat(item.reporter().nickname()).isNotBlank();
        assertThat(item.target()).isNotNull();
        assertThat(item.target().videoSeq()).isEqualTo(videoSeq);
        assertThat(item.processedBy()).isNull();
    }

    @Test
    @DisplayName("목록 — 상태 필터가 걸리고 접수순으로 온다")
    void list() {
        long first = newReport(reporter);
        long second = newReport(data.newUser());

        assertThat(adminReportService.list(ReportStatus.RECEIVED, null, 0, 20).list())
                .extracting(ReportItem::reportSeq)
                .containsSubsequence(first, second);
    }

    @Test
    @DisplayName("같은 대상에 쌓인 신고 수를 센다 — 여러 명이 신고한 것을 먼저 보라는 신호다")
    void sameTargetReportCount() {
        long reportSeq = newReport(reporter);
        newReport(data.newUser());

        assertThat(adminReportService.detail(reportSeq).sameTargetReportCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("미존재 신고는 404")
    void notFound() {
        assertThatThrownBy(() -> adminReportService.detail(999_999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AdminErrorCode.REPORT_NOT_FOUND);
    }

    private long newReport(Integer reporterSeq) {
        reportRepository.insertVideoReport(reporterSeq, videoSeq, "SPAM");
        entityManager.flush();
        return reportRepository.findByReporterSeqAndVideoSeq(reporterSeq, videoSeq)
                .orElseThrow().getReportSeq();
    }
}
