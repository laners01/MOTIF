package com.ssafy.motif.coreapi.domain.asset.controller;

import com.ssafy.motif.common.annotation.ApiController;
import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.common.response.ListResponse;
import com.ssafy.motif.coreapi.domain.asset.docs.ShopControllerDocs;
import com.ssafy.motif.coreapi.domain.asset.dto.ShopSectionItem;
import com.ssafy.motif.coreapi.domain.asset.service.ShopQueryService;
import com.ssafy.motif.coreapi.global.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 팬 앱 숍 API (명세 §11.3). Base URL 은 {@code /api/v1}.
 *
 * <p><b>{@link AssetController} 와 나뉜 이유는 경로다</b> — 저쪽은
 * {@code /api/v1/motif/assets}, 여기는 {@code /api/v1/motif/shop} 이다. 읽는 데이터가
 * 같은 에셋이라 패키지는 함께 둔다.
 *
 * <p><b>인증이 필요하다</b> — 구독·패스·구매가 전부 호출자 기준이라 principal 없이는
 * 답이 없다.
 *
 * <p>경로가 {@code /api/v1/**} 이고 {@code /api/v1/admin/**} 이 아니므로 springdoc
 * {@code user} 그룹 문서에 자동으로 잡힌다({@code OpenApiConfig}).
 *
 * <p>Swagger 문서는 {@link ShopControllerDocs} 가 갖는다.
 */
@ApiController
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/motif/shop")
public class ShopController implements ShopControllerDocs {

    private final ShopQueryService shopQueryService;

    /**
     * §11.3 숍 전량 — <b>비페이지</b>(§5.4 와 같은 봉투).
     *
     * <p>파라미터가 없다. 대상은 "내 구독" 하나뿐이고, 화면이 섹션을 전부 펼쳐 놓는
     * 구조라 잘라 줄 축이 없다 — 아티스트별로 더 보고 싶으면 §11.1·§11.2 로 들어간다.
     */
    @Override
    @GetMapping
    public ApiResponse<ListResponse<ShopSectionItem>> shop(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(shopQueryService.shop(user.userSeq()));
    }
}
