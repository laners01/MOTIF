package com.ssafy.motif.coreapi.admin.org;

import com.ssafy.motif.common.annotation.ApiController;
import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.common.response.PageResponse;
import com.ssafy.motif.coreapi.admin.AdminPaths;
import com.ssafy.motif.coreapi.admin.org.dto.GroupCreateRequest;
import com.ssafy.motif.coreapi.admin.org.dto.GroupCreateResponse;
import com.ssafy.motif.coreapi.admin.org.dto.GroupSummaryResponse;
import com.ssafy.motif.coreapi.admin.org.dto.GroupUpdateRequest;
import com.ssafy.motif.coreapi.admin.security.AuthenticatedAdmin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 어드민 그룹 API (어드민 명세 §3.4) — 소속사는 {@link AdminCompanyController} 다. */
@Tag(name = "Admin Artist Group", description = "아티스트 그룹 관리 (어드민 명세 §3.4)")
@ApiController
@RestController
@RequiredArgsConstructor
@RequestMapping(AdminPaths.API + "/groups")
public class AdminGroupController {

    private final AdminGroupService adminGroupService;

    @Operation(summary = "그룹 목록",
            description = "그룹명 부분 일치 · 소속사 필터. 비삭제 멤버 수를 함께 준다. 최신 등록순 offset 페이지.")
    @GetMapping
    public ApiResponse<PageResponse<GroupSummaryResponse>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer companySeq,
            @RequestParam(defaultValue = "false") boolean includeDeleted,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(adminGroupService.list(q, companySeq, includeDeleted, page, size));
    }

    @Operation(summary = "그룹 등록", description = "그룹명은 중복을 허용한다. 소속사는 비삭제여야 한다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<GroupCreateResponse> create(@AuthenticationPrincipal AuthenticatedAdmin admin,
                                                   @Valid @RequestBody GroupCreateRequest request) {
        return ApiResponse.success(adminGroupService.create(admin, request));
    }

    @Operation(summary = "그룹 수정",
            description = "부분 수정. companySeq 를 바꾸면 비삭제 멤버 전원의 소속사가 "
                    + "같은 트랜잭션에서 따라 옮겨진다(DB §8).")
    @PatchMapping("/{groupSeq}")
    public ApiResponse<GroupSummaryResponse> update(@AuthenticationPrincipal AuthenticatedAdmin admin,
                                                    @PathVariable int groupSeq,
                                                    @Valid @RequestBody GroupUpdateRequest request) {
        return ApiResponse.success(adminGroupService.update(admin, groupSeq, request));
    }

    @Operation(summary = "그룹 삭제",
            description = "소프트 삭제. 비삭제 멤버가 있으면 409 GROUP_IN_USE 이고 data 에 memberCount 를 싣는다.")
    @DeleteMapping("/{groupSeq}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthenticatedAdmin admin,
                                    @PathVariable int groupSeq) {
        adminGroupService.delete(admin, groupSeq);
        return ApiResponse.success();
    }
}
