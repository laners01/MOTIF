package com.ssafy.motif.coreapi.domain.shop.service;

import com.ssafy.motif.coreapi.domain.shop.dto.ShopBannerItem;
import com.ssafy.motif.coreapi.global.media.MediaSigningTestSupport;
import com.ssafy.motif.domain.core.banner.ShopBanner;
import com.ssafy.motif.domain.core.banner.ShopBannerRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * §11.11 숍 공용 배너를 실제 DB 에 대고 검증한다.
 *
 * <p><b>이 테이블은 전역이다.</b> 다른 목록 API 와 달리 사용자·아티스트로 좁힐 축이
 * 없어서, 로컬 compose DB 를 공유하는 다른 레인의 배너가 섞인다. 그래서 단언을
 * <b>이 테스트가 만든 배너로만</b> 좁힌다 — 전체 크기나 첫 항목을 단정하지 않는다.
 *
 * <p>{@code display_order} 가 UNIQUE 라 픽스처도 충돌하지 않을 값을 잡아야 한다
 * ({@code uq_shop_banners_order}).
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class ShopBannerLocalDbTest {

    @DynamicPropertySource
    static void media(DynamicPropertyRegistry registry) {
        MediaSigningTestSupport.register(registry);
    }

    @Autowired
    private ShopBannerQueryService shopBannerQueryService;
    @Autowired
    private ShopBannerRepository shopBannerRepository;
    @PersistenceContext
    private EntityManager entityManager;

    /** 이 테스트가 쓰는 순서 구간 — 다른 레인·시드와 부딪히지 않게 위쪽을 쓴다. */
    private short nextOrder;

    @BeforeEach
    void setUp() {
        int after = shopBannerRepository.findFirstByOrderByDisplayOrderDesc()
                .map(banner -> banner.getDisplayOrder() + 1)
                .orElse(1_000);
        nextOrder = (short) after;
    }

    @Test
    @DisplayName("활성 배너가 노출 순서 오름차순으로 나온다")
    void activeBannersComeInDisplayOrder() {
        short secondOrder = nextOrder();
        short firstOrder = nextOrder();
        Integer second = newBanner(secondOrder, true);
        Integer first = newBanner(firstOrder, true);

        List<Integer> mine = seqsOf(second, first);

        // secondOrder < firstOrder 이므로 second 가 앞이다 — seq 순서가 아니라 order 순서다
        assertThat(mine).containsExactly(second, first);
    }

    @Test
    @DisplayName("비활성 배너는 빠진다 — 어드민 §3.11.1 은 전량을 주는 것과 다르다")
    void inactiveBannerIsExcluded() {
        Integer active = newBanner(nextOrder(), true);
        Integer inactive = newBanner(nextOrder(), false);

        assertThat(seqsOf(active, inactive)).containsExactly(active);
        // 어드민 조회는 그대로 전량이다
        assertThat(shopBannerRepository.findAllByOrderByDisplayOrderAsc())
                .extracting(ShopBanner::getShopBannerSeq)
                .contains(active, inactive);
    }

    @Test
    @DisplayName("imageUrl 은 키가 아니라 무서명 CDN 주소다 — 서명을 붙이지 않는다")
    void imageUrlIsUnsigned() {
        String key = "banners/" + UUID.randomUUID() + ".jpg";
        Integer seq = newBanner(key, nextOrder(), true);

        ShopBannerItem item = itemOf(seq);

        assertThat(item.imageUrl()).isEqualTo(MediaSigningTestSupport.CDN + "/" + key);
        assertThat(item.imageUrl()).doesNotContain("Signature=", "Policy=", "Expires=");
    }

    @Test
    @DisplayName("항목은 세 칸뿐이다 — bannerSeq·imageUrl·displayOrder")
    void itemCarriesThreeFields() {
        short order = nextOrder();
        Integer seq = newBanner(order, true);

        ShopBannerItem item = itemOf(seq);

        assertThat(item.bannerSeq()).isEqualTo(seq);
        assertThat(item.displayOrder()).isEqualTo(order);
        assertThat(item.imageUrl()).isNotBlank();
    }

    // --- 픽스처 -------------------------------------------------------------

    private short nextOrder() {
        return nextOrder++;
    }

    private List<Integer> seqsOf(Integer... mine) {
        List<Integer> wanted = List.of(mine);
        return shopBannerQueryService.banners().list().stream()
                .map(ShopBannerItem::bannerSeq)
                .filter(wanted::contains)
                .toList();
    }

    private ShopBannerItem itemOf(Integer bannerSeq) {
        return shopBannerQueryService.banners().list().stream()
                .filter(item -> item.bannerSeq().equals(bannerSeq))
                .findFirst()
                .orElseThrow();
    }

    private Integer newBanner(short displayOrder, boolean active) {
        return newBanner("banners/" + UUID.randomUUID() + ".jpg", displayOrder, active);
    }

    private Integer newBanner(String imageKey, short displayOrder, boolean active) {
        ShopBanner banner = ShopBanner.register(imageKey, displayOrder);
        if (!active) {
            banner.changeActive(false);
        }
        Integer seq = shopBannerRepository.saveAndFlush(banner).getShopBannerSeq();
        entityManager.clear();
        return seq;
    }
}
