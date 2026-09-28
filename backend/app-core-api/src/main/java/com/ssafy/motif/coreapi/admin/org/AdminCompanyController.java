package com.ssafy.motif.coreapi.admin.org;

import com.ssafy.motif.common.annotation.ApiController;
import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.common.response.PageResponse;
import com.ssafy.motif.coreapi.admin.AdminPaths;
import com.ssafy.motif.coreapi.admin.org.dto.CompanyCreateResponse;
import com.ssafy.motif.coreapi.admin.org.dto.CompanyNameRequest;
import com.ssafy.motif.coreapi.admin.org.dto.CompanySummaryResponse;
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

/** 어드민 소속사 API (어드민 명세 §3.3). */
@Tag(name = "Admin Company", description = "소속사 관리 (어드민 명세 §3.3)")
@ApiController
@RestController
@RequiredArgsConstructor
@RequestMapping(AdminPaths.API + "/companies")
public class AdminCompanyController {

    private final AdminCompanyService adminCompanyService;

    @Operation(summary = "소속사 목록",
            description = "회사명 부분 일치. 비삭제 그룹·아티스트 참조 수를 함께 준다. 최신 등록순 offset 페이지.")
    @GetMapping
    public ApiResponse<PageResponse<CompanySummaryResponse>> list(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "false") boolean includeDeleted,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(adminCompanyService.list(q, includeDeleted, page, size));
    }

    @Operation(summary = "소속사 등록",
            description = "활성 회사명은 UNIQUE 다 — 중복이면 409 COMPANY_NAME_DUPLICATE.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CompanyCreateResponse> create(@AuthenticationPrincipal AuthenticatedAdmin admin,
                                                     @Valid @RequestBody CompanyNameRequest request) {
        return ApiResponse.success(adminCompanyService.create(admin, request));
    }

    @Operation(summary = "소속사 수정", description = "회사명만 바꾼다. 같은 이름을 그대로 보내는 것은 중복이 아니다.")
    @PatchMapping("/{companySeq}")
    public ApiResponse<CompanySummaryResponse> update(@AuthenticationPrincipal AuthenticatedAdmin admin,
                                                      @PathVariable int companySeq,
                                                      @Valid @RequestBody CompanyNameRequest request) {
        return ApiResponse.success(adminCompanyService.update(admin, companySeq, request));
    }

    @Operation(summary = "소속사 삭제",
            description = "소프트 삭제. 비삭제 그룹·아티스트가 참조 중이면 409 COMPANY_IN_USE 이고 "
                    + "data 에 groupCount·artistCount 를 싣는다.")
    @DeleteMapping("/{companySeq}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthenticatedAdmin admin,
                                    @PathVariable int companySeq) {
        adminCompanyService.delete(admin, companySeq);
        return ApiResponse.success();
    }
}
