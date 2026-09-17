package com.ssafy.motif.coreapi.domain.asset.controller;

import com.ssafy.motif.common.annotation.ApiController;
import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.common.response.PageResponse;
import com.ssafy.motif.coreapi.domain.asset.docs.AssetControllerDocs;
import com.ssafy.motif.coreapi.domain.asset.dto.AssetCatalogItem;
import com.ssafy.motif.coreapi.domain.asset.dto.AssetDetailResponse;
import com.ssafy.motif.coreapi.domain.asset.service.AssetQueryService;
import com.ssafy.motif.coreapi.global.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 팬 앱 에셋 API (명세 §11.1 · §11.2). Base URL 은 {@code /api/v1}.
 *
 * <p><b>전부 인증이 필요하다</b> — 목록의 기본 모드가 "내 구독 아티스트"이고, 두 API 의
 * {@code myAccess} 와 노출 판정이 호출자 기준이라 principal 없이는 답이 없다.
 *
 * <p>경로가 {@code /api/v1/**} 이고 {@code /api/v1/admin/**} 이 아니므로 springdoc
 * {@code user} 그룹 문서에 자동으로 잡힌다({@code OpenApiConfig}).
 *
 * <p>Swagger 문서는 {@link AssetControllerDocs} 가 갖는다.
 */
@ApiController
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/motif/assets")
public class AssetController implements AssetControllerDocs {

    private final AssetQueryService assetQueryService;

    /**
     * §11.1 에셋 목록 — <b>offset 페이지</b>.
     *
     * <p>커서가 아니다. 숍은 아티스트별 섹션을 훑는 화면이라 "페이지를 넘기는 동안 새
     * 항목이 밀려드는" 피드가 아니고, 어드민 등록으로만 늘어나는 목록이다.
     *
     * <p>{@code artistSeq} 는 {@code required=false} 다 — <b>없는 것이 기본 모드</b>이지
     * 잘못된 호출이 아니다.
     */
    @Override
    @GetMapping
    public ApiResponse<PageResponse<AssetCatalogItem>> assets(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) Integer artistSeq,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(
                assetQueryService.assets(user.userSeq(), artistSeq, page, size));
    }

    /**
     * §11.2 에셋 상세 — 구매 화면의 데이터.
     *
     * <p>목록과 달리 <b>구독을 보지 않는다</b>. 아티스트 상세(§5)·딥링크로 들어오는
     * 자리라 미구독이 정상 경로다.
     */
    @Override
    @GetMapping("/{assetSeq}")
    public ApiResponse<AssetDetailResponse> detail(@AuthenticationPrincipal AuthenticatedUser user,
                                                   @PathVariable Integer assetSeq) {
        return ApiResponse.success(assetQueryService.detail(user.userSeq(), assetSeq));
    }
}
