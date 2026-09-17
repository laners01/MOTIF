package com.ssafy.motif.coreapi.domain.asset.controller;

import com.ssafy.motif.common.annotation.ApiController;
import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.common.response.PageResponse;
import com.ssafy.motif.coreapi.domain.asset.docs.AssetControllerDocs;
import com.ssafy.motif.coreapi.domain.asset.dto.AssetCatalogItem;
import com.ssafy.motif.coreapi.domain.asset.service.AssetQueryService;
import com.ssafy.motif.coreapi.global.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 팬 앱 에셋 API (명세 §11.1). Base URL 은 {@code /api/v1}.
 *
 * <p><b>인증이 필요하다</b> — 기본 모드가 "내 구독 아티스트"이고 항목의 {@code myAccess}
 * 도 호출자 기준이라 principal 없이는 답이 없다.
 *
 * <p>§11.2 상세는 아직 없다 — 같은 {@code /motif/assets} 경로라도 별개 티켓이라 여기
 * 매핑이 없다.
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
}
