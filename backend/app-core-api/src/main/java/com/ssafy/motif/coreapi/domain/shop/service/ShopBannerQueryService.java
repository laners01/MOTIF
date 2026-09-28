package com.ssafy.motif.coreapi.domain.shop.service;

import com.ssafy.motif.common.response.ListResponse;
import com.ssafy.motif.coreapi.domain.shop.dto.ShopBannerItem;
import com.ssafy.motif.coreapi.global.media.MediaUrlResolver;
import com.ssafy.motif.domain.core.banner.ShopBanner;
import com.ssafy.motif.domain.core.banner.ShopBannerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 숍 공용 배너 조회 (API 명세 §11.11).
 *
 * <p>조회 한 번으로 끝난다 — 배너는 아티스트도 권한도 걸리지 않는 공개 콘텐츠다.
 * 여기서 하는 일은 활성 배너를 순서대로 읽어 키를 URL 로 바꾸는 것뿐이다.
 *
 * <p><b>아티스트별 패스 배너(§11.4 {@code bannerImageUrl})와 별개다.</b> 그쪽은
 * {@code artists.motif_bg_image_key} 이고 여기는 어드민이 §3.11 로 관리하는 공용 배너다.
 *
 * <p>읽기 전용 트랜잭션이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShopBannerQueryService {

    private final ShopBannerRepository shopBannerRepository;
    private final MediaUrlResolver mediaUrlResolver;

    /**
     * §11.11 배너 목록 — 활성만, 노출 순서 오름차순.
     *
     * <p>실패 분기가 없다. 배너가 0건이면 빈 배열이다 — 숍은 배너 없이도 그려진다.
     */
    public ListResponse<ShopBannerItem> banners() {
        return ListResponse.of(shopBannerRepository.findByActiveTrueOrderByDisplayOrderAsc()
                .stream()
                .map(this::item)
                .toList());
    }

    /**
     * 키 → URL.
     *
     * <p>배너는 무서명 프리픽스지만 {@link MediaUrlResolver} 를 거친다 — 등급 판정을
     * 호출부가 직접 하지 않는다는 규칙이 §1.7 이다. 프리픽스가 바뀌는 날 이 호출부만
     * 조용히 틀리는 일을 막는다.
     */
    private ShopBannerItem item(ShopBanner banner) {
        return new ShopBannerItem(
                banner.getShopBannerSeq(),
                mediaUrlResolver.resolve(banner.getImageKey()),
                banner.getDisplayOrder());
    }
}
