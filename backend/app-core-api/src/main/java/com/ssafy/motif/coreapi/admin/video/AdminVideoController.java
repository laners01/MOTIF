package com.ssafy.motif.coreapi.admin.video;

import com.ssafy.motif.common.annotation.ApiController;
import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.common.response.PageResponse;
import com.ssafy.motif.coreapi.admin.AdminPaths;
import com.ssafy.motif.coreapi.admin.video.dto.AdminVideoItem;
import com.ssafy.motif.domain.core.video.Visibility;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 어드민 참여 영상 탐색 (어드민 명세 §3.12.1·§3.12.2). */
@Tag(name = "Admin Video", description = "참여 영상 탐색 — PRIVATE·삭제 열람 (어드민 명세 §3.12)")
@ApiController
@RestController
@RequiredArgsConstructor
@RequestMapping(AdminPaths.API + "/videos")
public class AdminVideoController {

    private final AdminVideoService adminVideoService;

    @Operation(summary = "참여 영상 목록",
            description = "어드민은 PRIVATE·삭제분도 본다 — 신고 대상과 회원 상세를 확인해야 한다. "
                    + "항목은 §8.2 에 isDeleted·reportCount 를 더한 것이다. "
                    + "정렬은 현재 latest 만 지원한다(추천은 대행 UI 에서).")
    @GetMapping
    public ApiResponse<PageResponse<AdminVideoItem>> list(
            @RequestParam(required = false) Integer challengeSeq,
            @RequestParam(required = false) Integer userSeq,
            @RequestParam(required = false) Visibility visibility,
            @RequestParam(defaultValue = "false") boolean includeDeleted,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(adminVideoService
                .list(challengeSeq, userSeq, visibility, includeDeleted, sort, page, size));
    }

    @Operation(summary = "참여 영상 단건",
            description = "삭제분도 돌려준다 — 신고 상세가 '이미 지워졌으니 기각' 을 판단해야 한다. "
                    + "PRIVATE 영상의 재생 주소는 core-api 재생목록 경로다(§1.7).")
    @GetMapping("/{videoSeq}")
    public ApiResponse<AdminVideoItem> detail(@PathVariable long videoSeq) {
        return ApiResponse.success(adminVideoService.detail(videoSeq));
    }
}
