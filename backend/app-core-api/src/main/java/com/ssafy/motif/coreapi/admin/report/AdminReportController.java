package com.ssafy.motif.coreapi.admin.report;

import com.ssafy.motif.common.annotation.ApiController;
import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.common.response.PageResponse;
import com.ssafy.motif.coreapi.admin.AdminPaths;
import com.ssafy.motif.coreapi.admin.report.dto.ReportItem;
import com.ssafy.motif.coreapi.admin.report.dto.ReportProcessRequest;
import com.ssafy.motif.coreapi.admin.report.dto.ReportProcessResponse;
import com.ssafy.motif.coreapi.admin.security.AuthenticatedAdmin;
import com.ssafy.motif.domain.core.report.ReportStatus;
import com.ssafy.motif.domain.core.report.ReportTargetType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 어드민 신고 처리 (어드민 명세 §3.13). */
@Tag(name = "Admin Report", description = "신고 목록·상세·인용/기각 (어드민 명세 §3.13)")
@ApiController
@RestController
@RequiredArgsConstructor
@RequestMapping(AdminPaths.API + "/reports")
public class AdminReportController {

    private final AdminReportService adminReportService;

    @Operation(summary = "신고 목록",
            description = "접수순 고정 — 오래 방치된 것이 먼저 보여야 한다. "
                    + "댓글 대상은 §9 미구현이라 target 이 null 로 온다(항목은 숨기지 않는다).")
    @GetMapping
    public ApiResponse<PageResponse<ReportItem>> list(
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(required = false) ReportTargetType targetType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(adminReportService.list(status, targetType, page, size));
    }

    @Operation(summary = "신고 상세",
            description = "대상이 이미 삭제됐어도 준다 — '이미 지워졌으니 기각' 을 판단해야 한다.")
    @GetMapping("/{reportSeq}")
    public ApiResponse<ReportItem> detail(@PathVariable long reportSeq) {
        return ApiResponse.success(adminReportService.detail(reportSeq));
    }

    @Operation(summary = "신고 인용/기각",
            description = "ACCEPTED 면 대상을 소프트 삭제하고 participant_count 를 내린다. "
                    + "이미 삭제된 대상이면 상태만 전이한다(멱등) — 카운터를 두 번 내리지 않는다. "
                    + "RECEIVED 가 아니면 409 REPORT_ALREADY_PROCESSED.")
    @PostMapping("/{reportSeq}/process")
    public ApiResponse<ReportProcessResponse> process(
            @AuthenticationPrincipal AuthenticatedAdmin admin,
            @PathVariable long reportSeq,
            @Valid @RequestBody ReportProcessRequest request) {
        return ApiResponse.success(adminReportService.process(admin, reportSeq, request));
    }
}
