package com.ssafy.motif.coreapi.admin.notice;

import com.ssafy.motif.common.annotation.ApiController;
import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.common.response.PageResponse;
import com.ssafy.motif.coreapi.admin.AdminPaths;
import com.ssafy.motif.coreapi.admin.notice.dto.NoticeDetailResponse;
import com.ssafy.motif.coreapi.admin.notice.dto.NoticeListItemResponse;
import com.ssafy.motif.coreapi.admin.notice.dto.NoticeUpdateRequest;
import com.ssafy.motif.coreapi.admin.security.AuthenticatedAdmin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 어드민 공지 API (어드민 명세 §3.10) — 조회·수정·삭제. */
@Tag(name = "Admin Notice", description = "공지 조회·수정·삭제 (어드민 명세 §3.10)")
@ApiController
@RestController
@RequiredArgsConstructor
@RequestMapping(AdminPaths.API + "/notices")
public class AdminNoticeController {

    private final AdminNoticeService adminNoticeService;

    @Operation(summary = "공지 목록",
            description = "아티스트 필터·최신순 고정. includeDeleted=false 가 기본이며 "
                    + "삭제분은 지우지 않고 isDeleted 를 실어 보낸다(화면이 회색 처리).")
    @GetMapping
    public ApiResponse<PageResponse<NoticeListItemResponse>> list(
            @RequestParam(required = false) Integer artistSeq,
            @RequestParam(defaultValue = "false") boolean includeDeleted,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(adminNoticeService.list(artistSeq, includeDeleted, page, size));
    }

    @Operation(summary = "공지 상세", description = "본문 전체 + 이미지를 display_order 순으로 준다.")
    @GetMapping("/{noticeSeq}")
    public ApiResponse<NoticeDetailResponse> detail(@PathVariable int noticeSeq) {
        return ApiResponse.success(adminNoticeService.detail(noticeSeq));
    }

    @Operation(summary = "공지 수정",
            description = "imageKeys 가 오면 이미지를 전체 교체한다. 알림은 다시 보내지 않는다.")
    @PatchMapping("/{noticeSeq}")
    public ApiResponse<NoticeDetailResponse> update(
            @AuthenticationPrincipal AuthenticatedAdmin admin,
            @PathVariable int noticeSeq,
            @Valid @RequestBody NoticeUpdateRequest request) {
        return ApiResponse.success(adminNoticeService.update(admin, noticeSeq, request));
    }

    @Operation(summary = "공지 삭제", description = "소프트 삭제. 좋아요·이미지 행은 남는다.")
    @DeleteMapping("/{noticeSeq}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthenticatedAdmin admin,
                                    @PathVariable int noticeSeq) {
        adminNoticeService.delete(admin, noticeSeq);
        return ApiResponse.success();
    }
}
