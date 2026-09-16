package com.ssafy.motif.coreapi.admin.report;

import com.ssafy.motif.common.error.AdminErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.common.response.PageResponse;
import com.ssafy.motif.coreapi.admin.report.dto.ReportItem;
import com.ssafy.motif.coreapi.admin.video.AdminVideoService;
import com.ssafy.motif.coreapi.admin.video.dto.AdminVideoItem;
import com.ssafy.motif.coreapi.global.response.PageResponses;
import com.ssafy.motif.domain.core.report.Report;
import com.ssafy.motif.domain.core.report.ReportRepository;
import com.ssafy.motif.domain.core.report.ReportStatus;
import com.ssafy.motif.domain.core.report.ReportTargetType;
import com.ssafy.motif.domain.core.user.User;
import com.ssafy.motif.domain.core.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 신고 목록·상세·처리 (어드민 명세 §3.13) — S15P21D104-578·579.
 *
 * <p><b>지금 다룰 수 있는 대상은 참여 영상뿐이다.</b> 댓글 3종은 §9 댓글이 없어 미리보기를
 * 조립할 수도, 인용 시 공통 삭제 정책(답글 연쇄·좋아요 하드 삭제·{@code comment_count}
 * 감소)을 실행할 수도 없다. 다만 <b>그 행이 생길 수도 없다</b> — 댓글 신고 API(§9.6)가
 * 없어서 지금 {@code reports} 에 들어올 수 있는 것은 VIDEO 뿐이다.
 *
 * <p>그래도 목록에서 <b>숨기지는 않는다.</b> 숨기면 나중에 댓글 신고가 열렸을 때 처리
 * 못 하는 신고가 목록에서 사라져 영원히 방치된다 — 대상 미리보기를 {@code null} 로
 * 두고 화면이 "미지원" 을 표시한다.
 */
@Service
@RequiredArgsConstructor
public class AdminReportService {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final AdminVideoService adminVideoService;

    /** §3.13.1 — 접수순 고정. 오래 방치된 것이 먼저 보여야 한다. */
    @Transactional(readOnly = true)
    public PageResponse<ReportItem> list(ReportStatus status, ReportTargetType targetType,
                                         int page, int size) {
        Page<Report> reports = reportRepository.search(status,
                targetType == null ? null : targetType.name(),
                PageResponses.pageable(page, size));
        // 신고자 닉네임을 행마다 조회하면 한 페이지에 20번을 더 왕복한다
        Map<Integer, String> nicknames = nicknamesOf(reports.getContent());
        return PageResponses.of(reports.map(report -> item(report, nicknames)));
    }

    /** §3.13.2 — 상세. 대상이 이미 삭제됐어도 준다. */
    @Transactional(readOnly = true)
    public ReportItem detail(long reportSeq) {
        Report report = report(reportSeq);
        return item(report, nicknamesOf(List.of(report)));
    }




    private ReportItem item(Report report, Map<Integer, String> nicknames) {
        ReportTargetType type = report.targetType();
        // 지원하지 않는 대상은 null 로 둔다 — 항목을 숨기면 처리 못 하는 신고가
        // 목록에서 사라져 영원히 방치된다
        AdminVideoItem target = type == ReportTargetType.VIDEO
                ? adminVideoService.find(report.getVideoSeq()).orElse(null)
                : null;
        return new ReportItem(
                report.getReportSeq(),
                type,
                report.getReasonCode(),
                report.getStatus(),
                new ReportItem.Reporter(report.getReporterSeq(),
                        nicknames.get(report.getReporterSeq())),
                target,
                type == ReportTargetType.VIDEO
                        ? reportRepository.countByVideoSeq(report.getVideoSeq()) : 0L,
                report.getRegDttm(),
                report.getProcessedUsrSeq(),
                report.getProcessedDttm());
    }

    private Map<Integer, String> nicknamesOf(List<Report> reports) {
        List<Integer> seqs = reports.stream().map(Report::getReporterSeq).distinct().toList();
        return userRepository.findAllById(seqs).stream()
                .collect(Collectors.toMap(User::getUserSeq, User::getNickname));
    }

    private Report report(long reportSeq) {
        return reportRepository.findById(reportSeq)
                .orElseThrow(() -> new BusinessException(AdminErrorCode.REPORT_NOT_FOUND,
                        "미존재 신고: reportSeq=" + reportSeq));
    }

}
