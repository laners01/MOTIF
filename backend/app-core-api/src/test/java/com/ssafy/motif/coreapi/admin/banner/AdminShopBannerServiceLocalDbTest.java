package com.ssafy.motif.coreapi.admin.banner;

import com.ssafy.motif.common.error.AdminErrorCode;
import com.ssafy.motif.common.error.CommonErrorCode;
import com.ssafy.motif.common.exception.BusinessException;
import com.ssafy.motif.coreapi.admin.banner.dto.BannerCreateRequest;
import com.ssafy.motif.coreapi.admin.banner.dto.BannerItem;
import com.ssafy.motif.coreapi.admin.banner.dto.BannerOrderRequest;
import com.ssafy.motif.coreapi.admin.banner.dto.BannerUpdateRequest;
import com.ssafy.motif.coreapi.admin.media.AdminMediaService;
import com.ssafy.motif.coreapi.admin.security.AuthenticatedAdmin;
import com.ssafy.motif.domain.core.banner.ShopBanner;
import com.ssafy.motif.domain.core.banner.ShopBannerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * §3.11 을 실제 DB에 대고 검증한다 (S15P21D104-585·586).
 *
 * <p>보려는 것은 {@code uq_shop_banners_order} 와 부딪히지 않고 순서가 다시 깔리는가다.
 * 재정렬은 <b>조용히 깨지는</b> 종류라 — 밀어 올린 행이 그대로 남아도 예외가 나지
 * 않는다 — 인메모리 단위 테스트로는 잡히지 않는다. UNIQUE 제약과 벌크 UPDATE 의
 * 영속성 컨텍스트 동작이 함께 걸려야 의미가 있다.
 *
 * <p>{@link AdminMediaService} 만 목이다. HEAD 는 실제 S3 왕복이라 버킷·자격증명이
 * 필요해지는데, 여기서 보려는 것은 업로드 상태가 아니다.
 *
 * <p>실행 전 {@code backend/local-infra && docker compose up -d --wait}.
 */
@Tag("local-db")
@SpringBootTest
@Transactional
class AdminShopBannerServiceLocalDbTest {

    private static final AuthenticatedAdmin ADMIN = new AuthenticatedAdmin(1, 1, "운영자");

    @Autowired
    private AdminShopBannerService adminShopBannerService;
    @Autowired
    private ShopBannerRepository bannerRepository;

    @MockitoBean
    private AdminMediaService adminMediaService;

    @BeforeEach
    void setUp() {
        BDDMockito.given(adminMediaService.exists(BDDMockito.anyString())).willReturn(true);
        bannerRepository.deleteAllInBatch();
        bannerRepository.flush();
    }

    @Test
    @DisplayName("등록 — displayOrder 를 생략하면 맨 뒤(MAX+1)에 붙는다")
    void createAppends() {
        create("banners/a.jpg");
        create("banners/b.jpg");

        assertThat(orders()).containsExactly((short) 0, (short) 1);
    }

    @Test
    @DisplayName("등록 — 이미 쓰는 순서를 지정하면 409 BANNER_ORDER_DUPLICATE")
    void createDuplicateOrder() {
        create("banners/a.jpg");

        assertThatThrownBy(() -> adminShopBannerService.create(ADMIN,
                new BannerCreateRequest("banners/b.jpg", (short) 0)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AdminErrorCode.BANNER_ORDER_DUPLICATE);
    }

    @Test
    @DisplayName("재정렬 — 뒤집어도 UNIQUE 와 부딪히지 않고 0..n−1 이 다시 깔린다")
    void reorderReversed() {
        List<Integer> seqs = List.of(create("banners/a.jpg"), create("banners/b.jpg"),
                create("banners/c.jpg"));
        List<Integer> reversed = seqs.reversed();

        List<BannerItem> result = adminShopBannerService
                .reorder(ADMIN, new BannerOrderRequest(reversed)).list();

        assertThat(result).extracting(BannerItem::shopBannerSeq).containsExactlyElementsOf(reversed);
        assertThat(result).extracting(BannerItem::displayOrder)
                .containsExactly((short) 0, (short) 1, (short) 2);
    }

    @Test
    @DisplayName("재정렬 — 자리가 그대로인 행도 밀어 올린 값으로 남지 않는다")
    void reorderKeepsUnmovedRows() {
        // 벌크 UPDATE 로 전 행을 10000 씩 밀어 올린 뒤 영속성 컨텍스트를 비우지 않으면,
        // "원래 0 이었고 새 순서도 0" 인 행은 더티 체킹이 변경으로 보지 않아 UPDATE 가
        // 나가지 않는다 — 그 행만 10000 인 채로 남는데 예외도 로그도 없다
        int a = create("banners/a.jpg");
        int b = create("banners/b.jpg");
        int c = create("banners/c.jpg");

        adminShopBannerService.reorder(ADMIN, new BannerOrderRequest(List.of(a, c, b)));

        assertThat(orders()).containsExactly((short) 0, (short) 1, (short) 2);
        assertThat(adminShopBannerService.list().list())
                .extracting(BannerItem::shopBannerSeq).containsExactly(a, c, b);
    }

    @Test
    @DisplayName("재정렬 — 누락·중복·미존재는 400")
    void reorderMustBeComplete() {
        int a = create("banners/a.jpg");
        int b = create("banners/b.jpg");

        assertThatThrownBy(() -> adminShopBannerService
                .reorder(ADMIN, new BannerOrderRequest(List.of(a))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(CommonErrorCode.INVALID_PARAMETER);

        assertThatThrownBy(() -> adminShopBannerService
                .reorder(ADMIN, new BannerOrderRequest(List.of(a, a))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(CommonErrorCode.INVALID_PARAMETER);

        assertThatThrownBy(() -> adminShopBannerService
                .reorder(ADMIN, new BannerOrderRequest(List.of(a, b, 999_999))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(CommonErrorCode.INVALID_PARAMETER);
    }

    @Test
    @DisplayName("수정 — 이미지 교체와 노출 전환은 순서를 건드리지 않는다")
    void updateKeepsOrder() {
        create("banners/a.jpg");
        int b = create("banners/b.jpg");

        BannerItem updated = adminShopBannerService.update(ADMIN, b,
                new BannerUpdateRequest("banners/new.jpg", false));

        assertThat(updated.imageKey()).isEqualTo("banners/new.jpg");
        assertThat(updated.isActive()).isFalse();
        assertThat(updated.displayOrder()).isEqualTo((short) 1);
    }

    @Test
    @DisplayName("삭제 — 하드 삭제이고 남은 행의 순서는 그대로다(구멍이 나도 UNIQUE 는 성립)")
    void deleteLeavesGap() {
        int a = create("banners/a.jpg");
        create("banners/b.jpg");

        adminShopBannerService.delete(ADMIN, a);

        assertThat(orders()).containsExactly((short) 1);
    }

    private int create(String imageKey) {
        return adminShopBannerService.create(ADMIN, new BannerCreateRequest(imageKey, null))
                .shopBannerSeq();
    }

    private List<Short> orders() {
        return bannerRepository.findAllByOrderByDisplayOrderAsc().stream()
                .map(ShopBanner::getDisplayOrder)
                .toList();
    }
}
