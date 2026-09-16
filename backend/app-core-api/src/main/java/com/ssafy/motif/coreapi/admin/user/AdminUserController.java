package com.ssafy.motif.coreapi.admin.user;

import com.ssafy.motif.common.annotation.ApiController;
import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.common.response.PageResponse;
import com.ssafy.motif.coreapi.admin.AdminPaths;
import com.ssafy.motif.coreapi.admin.user.dto.UserDetailResponse;
import com.ssafy.motif.coreapi.admin.user.dto.UserSummaryResponse;
import com.ssafy.motif.domain.core.auth.AccountStatus;
import com.ssafy.motif.domain.core.user.UserRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 어드민 회원 API (어드민 명세 §3.14.1~2). 정지·해제(§3.14.3~4)는 별 티켓이다. */
@Tag(name = "Admin User", description = "회원 관리 (어드민 명세 §3.14)")
@ApiController
@RestController
@RequiredArgsConstructor
@RequestMapping(AdminPaths.API + "/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

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
        return ApiResponse.success(adminUserService.list(q, userSeq, role, accountStatus, page, size));
    }

    @Operation(summary = "회원 상세",
            description = "통계 6종(영상·구독·구매·패스·피신고·신고)과 보유 중인 모티프 패스를 함께 준다.")
    @GetMapping("/{userSeq}")
    public ApiResponse<UserDetailResponse> detail(@PathVariable Integer userSeq) {
        return ApiResponse.success(adminUserService.detail(userSeq));
    }
}
