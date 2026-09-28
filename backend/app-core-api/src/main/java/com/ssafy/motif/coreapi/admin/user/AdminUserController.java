package com.ssafy.motif.coreapi.admin.user;

import com.ssafy.motif.common.annotation.ApiController;
import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.common.response.PageResponse;
import com.ssafy.motif.coreapi.admin.AdminPaths;
import com.ssafy.motif.coreapi.admin.security.AuthenticatedAdmin;
import com.ssafy.motif.coreapi.admin.user.dto.UserDetailResponse;
import com.ssafy.motif.coreapi.admin.user.dto.UserSummaryResponse;
import com.ssafy.motif.coreapi.admin.user.dto.UserSuspendRequest;
import com.ssafy.motif.coreapi.admin.user.dto.UserSuspendResponse;
import com.ssafy.motif.coreapi.admin.user.dto.UserUnsuspendResponse;
import com.ssafy.motif.domain.core.auth.AccountStatus;
import com.ssafy.motif.domain.core.user.UserRole;
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

/** 어드민 회원 API (어드민 명세 §3.14) — 검색·상세·정지·해제. */
@Tag(name = "Admin User", description = "회원 관리 (어드민 명세 §3.14)")
@ApiController
@RestController
@RequiredArgsConstructor
@RequestMapping(AdminPaths.API + "/users")
public class AdminUserController {

    private final AdminUserQueryService adminUserQueryService;
    private final AdminUserCommandService adminUserCommandService;

    @Operation(summary = "회원 검색",
            description = "닉네임 부분 일치·역할·계정상태 필터. 최신 가입순 offset 페이지. "
                    + "삭제 회원도 포함해 내려준다(isDeleted 로 구분).")
    @GetMapping
    public ApiResponse<PageResponse<UserSummaryResponse>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer userSeq,
            @RequestParam(required = false) UserRole role,
            @RequestParam(required = false) AccountStatus accountStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(adminUserQueryService.list(q, userSeq, role, accountStatus, page, size));
    }

    @Operation(summary = "회원 상세",
            description = "통계 6종(영상·구독·구매·패스·피신고·신고)과 보유 중인 모티프 패스를 함께 준다.")
    @GetMapping("/{userSeq}")
    public ApiResponse<UserDetailResponse> detail(@PathVariable Integer userSeq) {
        return ApiResponse.success(adminUserQueryService.detail(userSeq));
    }

    @Operation(summary = "회원 정지",
            description = "계정을 SUSPENDED 로 바꾸고 refresh 토큰을 전부 폐기한다. "
                    + "이미 발급된 access 토큰은 만료까지 유효하다(§1.5). "
                    + "reason 은 저장되지 않고 감사 로그에만 남는다. "
                    + "아티스트·어드민은 정지 대상이 아니다(400).")
    @PostMapping("/{userSeq}/suspend")
    public ApiResponse<UserSuspendResponse> suspend(@AuthenticationPrincipal AuthenticatedAdmin admin,
                                                    @PathVariable Integer userSeq,
                                                    @Valid @RequestBody UserSuspendRequest request) {
        return ApiResponse.success(adminUserCommandService.suspend(admin, userSeq, request.reason()));
    }

    @Operation(summary = "회원 정지 해제",
            description = "ACTIVE 로 되돌린다. 폐기된 토큰은 복구하지 않는다 — 재로그인해야 한다.")
    @PostMapping("/{userSeq}/unsuspend")
    public ApiResponse<UserUnsuspendResponse> unsuspend(@AuthenticationPrincipal AuthenticatedAdmin admin,
                                                        @PathVariable Integer userSeq) {
        return ApiResponse.success(adminUserCommandService.unsuspend(admin, userSeq));
    }
}
