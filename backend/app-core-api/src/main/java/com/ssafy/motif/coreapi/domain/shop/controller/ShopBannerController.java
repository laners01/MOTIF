package com.ssafy.motif.coreapi.domain.shop.controller;

import com.ssafy.motif.common.annotation.ApiController;
import com.ssafy.motif.common.response.ApiResponse;
import com.ssafy.motif.common.response.ListResponse;
import com.ssafy.motif.coreapi.domain.shop.docs.ShopBannerControllerDocs;
import com.ssafy.motif.coreapi.domain.shop.dto.ShopBannerItem;
import com.ssafy.motif.coreapi.domain.shop.service.ShopBannerQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 팬 앱 숍 배너 API (명세 §11.11). Base URL 은 {@code /api/v1}.
 *
 * <p><b>경로가 {@code /api/v1/shop} 이라 컨트롤러가 따로다</b> — 숍 전량(§11.3)은
 * {@code /api/v1/motif/shop} 이고 에셋(§11.1·§11.2)은 {@code /api/v1/motif/assets} 다.
 * 이름이 비슷하지만 경로가 갈리므로 합칠 수 없다(이 레포는 경로가 컨트롤러를 정한다).
 *
 * <p>인증이 필요하다 — 다만 응답은 호출자에 따라 달라지지 않는다. 팬 앱 로그인 뒤
 * 화면이라 공개로 열지 않는다(§5.4 와 같은 판단).
 *
 * <p>경로가 {@code /api/v1/**} 이고 {@code /api/v1/admin/**} 이 아니므로 springdoc
 * {@code user} 그룹 문서에 자동으로 잡힌다({@code OpenApiConfig}).
 *
 * <p>Swagger 문서는 {@link ShopBannerControllerDocs} 가 갖는다.
 */
@ApiController
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/shop/banners")
public class ShopBannerController implements ShopBannerControllerDocs {

    private final ShopBannerQueryService shopBannerQueryService;

    /**
     * §11.11 숍 공용 배너 — <b>비페이지 전량</b>, 활성만.
     *
     * <p><b>principal 을 받지 않는다</b> — 응답에 "내" 상태가 없다. 인증 자체는
     * {@code SecurityConfig} 의 catch-all 이 걸며, 쓰지도 않을 값을 받아 두면 "여기서
     * 뭔가 사용자별로 갈린다"는 오해만 남는다(§5.4 와 같은 판단).
     */
    @Override
    @GetMapping
    public ApiResponse<ListResponse<ShopBannerItem>> banners() {
        return ApiResponse.success(shopBannerQueryService.banners());
    }
}
