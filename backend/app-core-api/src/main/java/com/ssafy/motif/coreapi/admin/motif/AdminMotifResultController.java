package com.ssafy.motif.coreapi.admin.motif;

import com.ssafy.motif.common.annotation.ApiController;
import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.common.response.PageResponse;
import com.ssafy.motif.coreapi.admin.AdminPaths;
import com.ssafy.motif.coreapi.admin.motif.dto.AdminMotifResultItem;
import com.ssafy.motif.coreapi.admin.motif.dto.MotifThumbnailRequest;
import com.ssafy.motif.coreapi.admin.motif.dto.MotifThumbnailResponse;
import com.ssafy.motif.coreapi.admin.security.AuthenticatedAdmin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 어드민 모티프 결과물 탐색 (어드민 명세 §3.17). */
@Tag(name = "Admin Motif Result", description = "모티프 결과물 탐색 — 전 사용자·삭제 열람 (어드민 명세 §3.17)")
@ApiController
@RestController
@RequiredArgsConstructor
@RequestMapping(AdminPaths.API + "/motif-results")
public class AdminMotifResultController {

    private final AdminMotifResultService adminMotifResultService;

    @Operation(summary = "모티프 결과물 목록",
            description = "전 사용자의 결과물을 최신순으로 준다. includeDeleted=true 면 삭제분도 포함한다.")
    @GetMapping
    public ApiResponse<PageResponse<AdminMotifResultItem>> list(
            @RequestParam(required = false) Integer userSeq,
            @RequestParam(required = false) Integer artistSeq,
            @RequestParam(required = false) Integer assetSeq,
            @RequestParam(defaultValue = "false") boolean includeDeleted,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(adminMotifResultService
                .list(userSeq, artistSeq, assetSeq, includeDeleted, page, size));
    }

    @Operation(summary = "모티프 결과물 썸네일 교체",
            description = "MOTIF_THUMBNAIL 로 presign 받아 올린 이미지 키를 준다. 영상은 바뀌지 않는다.")
    @PatchMapping("/{resultSeq}/thumbnail")
    public ApiResponse<MotifThumbnailResponse> changeThumbnail(
            @AuthenticationPrincipal AuthenticatedAdmin admin,
            @PathVariable long resultSeq,
            @Valid @RequestBody MotifThumbnailRequest request) {
        return ApiResponse.success(
                adminMotifResultService.changeThumbnail(admin, resultSeq, request.thumbnailKey()));
    }
}
