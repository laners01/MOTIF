package com.ssafy.motif.coreapi.domain.asset.controller;

import com.ssafy.motif.common.annotation.ApiController;
import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.common.response.ListResponse;
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
 * <p><b>전부 인증이 필요하다</b> — §11.1 은 "내가 산 것" 이고 §11.2 의 {@code myAccess}·
 * 노출 판정도 호출자 기준이라 principal 없이는 답이 없다.
 *
 * <p>숍 첫 화면은 여기가 아니라 {@link ShopController}(§11.3)다.
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
     * §11.1 내 보유함 — <b>비페이지 전량</b>(§5.4 와 같은 봉투).
     *
     * <p><b>페이지 파라미터를 받지 않는다</b>(S15P21D104-219). 한 사람이 산 것이라 카탈로그
     * 처럼 늘어나지 않고, 숍 첫 화면이던 자리는 §11.3 이 가져갔다.
     *
     * <p>{@code artistSeq} 는 {@code required=false} 다 — 없는 것이 기본(전체)이지 잘못된
     * 호출이 아니다.
     */
    @Override
    @GetMapping
    public ApiResponse<ListResponse<AssetCatalogItem>> assets(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) Integer artistSeq) {
        return ApiResponse.success(assetQueryService.assets(user.userSeq(), artistSeq));
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
