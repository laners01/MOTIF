package com.ssafy.motif.coreapi.admin.report;

import com.ssafy.motif.common.error.AdminErrorCode;
import com.ssafy.motif.common.error.CommonErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.common.response.PageResponse;
import com.ssafy.motif.coreapi.admin.audit.AdminAuditLogger;
import com.ssafy.motif.coreapi.admin.report.dto.ReportItem;
import com.ssafy.motif.coreapi.admin.report.dto.ReportProcessRequest;
import com.ssafy.motif.coreapi.admin.report.dto.ReportProcessResponse;
import com.ssafy.motif.coreapi.admin.security.AuthenticatedAdmin;
import com.ssafy.motif.coreapi.admin.video.AdminVideoService;
import com.ssafy.motif.coreapi.admin.video.dto.AdminVideoItem;
import com.ssafy.motif.coreapi.global.response.PageResponses;
import com.ssafy.motif.domain.core.challenge.ChallengeRepository;
import com.ssafy.motif.domain.core.report.Report;
import com.ssafy.motif.domain.core.report.ReportRepository;
import com.ssafy.motif.domain.core.report.ReportStatus;
import com.ssafy.motif.domain.core.report.ReportTargetType;
import com.ssafy.motif.domain.core.user.User;
import com.ssafy.motif.domain.core.user.UserRepository;
import com.ssafy.motif.domain.core.video.ParticipationVideo;
import com.ssafy.motif.domain.core.video.ParticipationVideoRepository;
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
 *
 * <p><b>인용은 멱등이다</b>(§3.13.3 · DB §26). 이미 삭제된 대상이면 상태만 전이하고
 * 후처리는 하지 않는다 — {@code participant_count} 를 두 번 내리면 영영 어긋난다.
 */
@Service
@RequiredArgsConstructor
public class AdminReportService {

    private final ReportRepository reportRepository;
    private final ParticipationVideoRepository videoRepository;
    private final ChallengeRepository challengeRepository;
    private final UserRepository userRepository;
    private final AdminVideoService adminVideoService;
    private final AdminAuditLogger auditLogger;

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

    /**
     * §3.13.3 — 인용/기각.
     *
     * <p>ACCEPTED 면 대상을 소프트 삭제하고 후처리를 돈다. 삭제가 <b>실제로 일어났을
     * 때만</b>({@code softDelete} 가 1행) {@code participant_count} 를 내린다.
     *
     * <p>영상 삭제 자체는 {@code ParticipationVideoRepository.softDelete} 가 한다 —
     * §8.10 본인 삭제도 같은 메서드를 쓴다. 여기에 따로 구현하면 두 경로의 카운터
     * 처리가 갈린다.
     */
    @Transactional
    public ReportProcessResponse process(AuthenticatedAdmin admin, long reportSeq,
                                         ReportProcessRequest request) {
        if (!request.isTerminal()) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER,
                    "decision 은 ACCEPTED 또는 REJECTED 다: " + request.decision());
        }
        Report report = report(reportSeq);
        if (!report.isReceived()) {
            throw new BusinessException(AdminErrorCode.REPORT_ALREADY_PROCESSED,
                    "이미 처리된 신고: reportSeq=%d status=%s".formatted(reportSeq, report.getStatus()),
                    Map.of("status", report.getStatus().name()));
        }
        requireSupportedTarget(report);

        ReportProcessResponse.SideEffects effects = ReportProcessResponse.SideEffects.NONE;
        boolean targetDeleted = false;
        if (request.isAccepted()) {
            targetDeleted = true;                   // 이 분기에 온 시점에 대상은 반드시 삭제 상태가 된다
            effects = deleteVideoTarget(admin, report.getVideoSeq());
        } else {
            targetDeleted = isDeleted(report.getVideoSeq());
        }
        report.process(request.decision(), admin.userSeq());

        auditLogger.write(admin, "PROCESS_REPORT", "REPORT", reportSeq,
                AdminAuditLogger.RESULT_OK, request.decision().name());
        return new ReportProcessResponse(reportSeq, request.decision(), targetDeleted, effects);
    }

    /**
     * 대상 삭제 + 후처리.
     *
     * <p>{@code softDelete} 가 0행이면 이미 삭제된 것이다 — 상태만 전이하고 카운터는
     * 건드리지 않는다. 이게 멱등의 전부다.
     */
    private ReportProcessResponse.SideEffects deleteVideoTarget(AuthenticatedAdmin admin, Long videoSeq) {
        if (videoRepository.softDelete(videoSeq, admin.userSeq()) == 0) {
            return ReportProcessResponse.SideEffects.NONE;
        }
        // 삭제된 영상이 속한 챌린지의 참여 수를 내린다. 영상 행은 남아 있으므로
        // challenge_seq 를 여기서 읽을 수 있다(하드 삭제가 아니다)
        int delta = videoRepository.findById(videoSeq)
                .map(ParticipationVideo::getChallengeSeq)
                .map(challengeRepository::decrementParticipantCount)
                .orElse(0);
        // has_artist_* 는 재계산하지 않는다 — 영상 자체가 비노출이라 정렬 대상이 아니다(§3.13.3)
        return new ReportProcessResponse.SideEffects(-delta, 0);
    }

    private boolean isDeleted(Long videoSeq) {
        return videoRepository.findById(videoSeq)
                .map(ParticipationVideo::isDeleted)
                .orElse(true);
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

    /**
     * 댓글 대상은 아직 처리할 수 없다.
     *
     * <p>조용히 상태만 바꾸면 <b>대상이 살아 있는데 인용으로 닫힌다.</b> 신고자에게는
     * 처리됐다고 보이고 실제로는 아무 일도 안 일어난다 — 409 로 막고 §9 를 기다린다.
     */
    private static void requireSupportedTarget(Report report) {
        if (report.targetType() != ReportTargetType.VIDEO) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER,
                    "댓글 신고 처리는 §9 댓글 구현 후에 연다: targetType=" + report.targetType());
        }
    }
}
