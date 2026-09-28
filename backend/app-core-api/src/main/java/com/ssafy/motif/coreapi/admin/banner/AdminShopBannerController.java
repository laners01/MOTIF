package com.ssafy.motif.coreapi.admin.banner;

import com.ssafy.motif.common.annotation.ApiController;
import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.common.response.ListResponse;
import com.ssafy.motif.coreapi.admin.AdminPaths;
import com.ssafy.motif.coreapi.admin.banner.dto.BannerCreateRequest;
import com.ssafy.motif.coreapi.admin.banner.dto.BannerCreateResponse;
import com.ssafy.motif.coreapi.admin.banner.dto.BannerItem;
import com.ssafy.motif.coreapi.admin.banner.dto.BannerOrderRequest;
import com.ssafy.motif.coreapi.admin.banner.dto.BannerUpdateRequest;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 어드민 숍 배너 API (어드민 명세 §3.11). */
@Tag(name = "Admin Shop Banner", description = "숍 공용 배너 목록·등록·수정·재정렬·삭제 (어드민 명세 §3.11)")
@ApiController
@RestController
@RequiredArgsConstructor
@RequestMapping(AdminPaths.API + "/shop-banners")
public class AdminShopBannerController {

    private final AdminShopBannerService adminShopBannerService;

    @Operation(summary = "배너 목록",
            description = "전량(비활성 포함)을 display_order 오름차순으로 준다. "
                    + "페이지가 없다 — 재정렬이 전량을 요구한다.")
    @GetMapping
    public ApiResponse<ListResponse<BannerItem>> list() {
        return ApiResponse.success(adminShopBannerService.list());
    }

    @Operation(summary = "배너 등록",
            description = "displayOrder 를 생략하면 맨 뒤(MAX+1). 지정했는데 이미 쓰는 "
                    + "순서면 409 BANNER_ORDER_DUPLICATE. 이미지 키는 HEAD 로 확인한다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BannerCreateResponse> create(
            @AuthenticationPrincipal AuthenticatedAdmin admin,
            @Valid @RequestBody BannerCreateRequest request) {
        return ApiResponse.success(adminShopBannerService.create(admin, request));
    }

    @Operation(summary = "배너 수정",
            description = "이미지 교체·활성 전환. 보낸 필드만 반영한다. "
                    + "순서는 여기서 바꾸지 않는다 — 전체 재정렬을 쓴다.")
    @PatchMapping("/{shopBannerSeq}")
    public ApiResponse<BannerItem> update(
            @AuthenticationPrincipal AuthenticatedAdmin admin,
            @PathVariable int shopBannerSeq,
            @Valid @RequestBody BannerUpdateRequest request) {
        return ApiResponse.success(adminShopBannerService.update(admin, shopBannerSeq, request));
    }

    @Operation(summary = "배너 전체 재정렬",
            description = "배열 순서가 곧 display_order 0..n−1 이다. 전량이어야 하며 "
                    + "중복·누락·미존재 seq 는 400. 응답은 재정렬된 목록.")
    @PutMapping("/order")
    public ApiResponse<ListResponse<BannerItem>> reorder(
            @AuthenticationPrincipal AuthenticatedAdmin admin,
            @Valid @RequestBody BannerOrderRequest request) {
        return ApiResponse.success(adminShopBannerService.reorder(admin, request));
    }

    @Operation(summary = "배너 삭제", description = "하드 삭제. 참조하는 곳이 없다.")
    @DeleteMapping("/{shopBannerSeq}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal AuthenticatedAdmin admin,
                                    @PathVariable int shopBannerSeq) {
        adminShopBannerService.delete(admin, shopBannerSeq);
        return ApiResponse.success();
    }
}
