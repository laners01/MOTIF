package com.ssafy.motif.coreapi.admin.notice;

import com.ssafy.motif.common.annotation.ApiController;
import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.coreapi.admin.AdminPaths;
import com.ssafy.motif.coreapi.admin.notice.dto.NoticeDetailResponse;
import com.ssafy.motif.coreapi.admin.notice.dto.NoticeUpdateRequest;
import com.ssafy.motif.coreapi.admin.security.AuthenticatedAdmin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 어드민 공지 API (어드민 명세 §3.10.4·§3.10.5) — 수정·삭제. */
@Tag(name = "Admin Notice", description = "공지 수정·삭제 (어드민 명세 §3.10)")
@ApiController
@RestController
@RequiredArgsConstructor
@RequestMapping(AdminPaths.API + "/notices")
public class AdminNoticeController {

    private final AdminNoticeService adminNoticeService;

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
