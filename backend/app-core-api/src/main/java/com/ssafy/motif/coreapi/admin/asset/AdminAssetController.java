package com.ssafy.motif.coreapi.admin.asset;

import com.ssafy.motif.common.annotation.ApiController;
import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.coreapi.admin.AdminPaths;
import com.ssafy.motif.coreapi.admin.asset.dto.AssetActiveRequest;
import com.ssafy.motif.coreapi.admin.asset.dto.AssetActiveResponse;
import com.ssafy.motif.coreapi.admin.asset.dto.AssetCreateRequest;
import com.ssafy.motif.coreapi.admin.asset.dto.AssetCreateResponse;
import com.ssafy.motif.coreapi.admin.asset.dto.AssetListItem;
import com.ssafy.motif.coreapi.admin.asset.dto.AssetPriceRequest;
import com.ssafy.motif.coreapi.admin.asset.dto.AssetPriceResponse;
import com.ssafy.motif.coreapi.admin.security.AuthenticatedAdmin;
import com.ssafy.motif.coreapi.global.response.PageResponse;
import com.ssafy.motif.domain.core.asset.AssetType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 어드민 모티프 에셋 API (어드민 명세 §3.8) — 등록·활성 전환·국가 가격. */
@Tag(name = "Admin Asset", description = "모티프 에셋 등록·활성·가격 (어드민 명세 §3.8)")
@ApiController
@RestController
@RequiredArgsConstructor
@RequestMapping(AdminPaths.API + "/motif-assets")
public class AdminAssetController {

    private final AdminAssetService adminAssetService;

    @Operation(summary = "에셋 목록",
            description = "아티스트·활성·타입으로 거르고 offset 페이지로 준다(§1.5 — size 기본 20·최대 100). "
                    + "price 는 KR 행이 없으면 null 이다.")
    @GetMapping
    public ApiResponse<PageResponse<AssetListItem>> list(
            @RequestParam(required = false) Integer artistSeq,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) AssetType assetType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(adminAssetService.list(
                artistSeq, isActive, assetType, PageResponse.pageable(page, size)));
    }

    @Operation(summary = "에셋 등록", description = "에셋과 KR 가격을 한 트랜잭션에 만든다.")
    @PostMapping
    public ResponseEntity<ApiResponse<AssetCreateResponse>> create(
            @AuthenticationPrincipal AuthenticatedAdmin admin,
            @Valid @RequestBody AssetCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(adminAssetService.create(admin, request)));
    }

    @Operation(summary = "에셋 활성/비활성",
            description = "비활성화는 해당 에셋의 가격을 전부 내린다. 재활성화는 가격을 되살리지 않는다.")
    @PatchMapping("/{assetSeq}/active")
    public ApiResponse<AssetActiveResponse> changeActive(
            @AuthenticationPrincipal AuthenticatedAdmin admin,
            @PathVariable int assetSeq,
            @Valid @RequestBody AssetActiveRequest request) {
        return ApiResponse.success(adminAssetService.changeActive(admin, assetSeq, request.isActive()));
    }

    @Operation(summary = "국가 가격 upsert", description = "(assetSeq, countryCode) 기준 등록 또는 갱신.")
    @PutMapping("/{assetSeq}/prices/{countryCode}")
    public ApiResponse<AssetPriceResponse> upsertPrice(
            @AuthenticationPrincipal AuthenticatedAdmin admin,
            @PathVariable int assetSeq,
            @PathVariable String countryCode,
            @Valid @RequestBody AssetPriceRequest request) {
        return ApiResponse.success(adminAssetService.upsertPrice(admin, assetSeq, countryCode, request));
    }
}
