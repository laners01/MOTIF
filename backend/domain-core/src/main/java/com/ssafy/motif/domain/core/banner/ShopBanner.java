package com.ssafy.motif.domain.core.banner;

import com.ssafy.motif.common.jpa.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 모티프 숍 상단 공용 배너 (DB SPEC v0.15 §40, changeSet 0011-01).
 *
 * <p>아티스트별 {@code artists.motif_bg_image_key} 와 별개다 — 이쪽은 아티스트에 매이지
 * 않은 숍 전체 배너이고 어드민만 만든다. 클릭 액션 컬럼은 도입하지 않았다.
 *
 * <p><b>{@code is_deleted} 가 없다.</b> 참조하는 곳이 없어 하드 삭제한다(어드민 명세
 * §3.11.5). 노출만 끄고 싶으면 {@code is_active} 를 쓴다.
 *
 * <p><b>{@code display_order} 는 UNIQUE 다</b>({@code uq_shop_banners_order}).
 * 활성·비활성이 순서 공간을 함께 쓰므로, 비활성 배너도 자리를 하나 차지한다.
 */
@Entity
@Table(name = "shop_banners")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShopBanner extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "shop_banner_seq")
    private Integer shopBannerSeq;

    /** {@code banners/{uuid}.jpg} — 공개 등급이다(§1.7). */
    @Column(name = "image_key", nullable = false)
    private String imageKey;

    @Column(name = "display_order", nullable = false)
    private short displayOrder;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    public static ShopBanner register(String imageKey, short displayOrder) {
        ShopBanner banner = new ShopBanner();
        banner.imageKey = imageKey;
        banner.displayOrder = displayOrder;
        banner.active = true;
        return banner;
    }

    /**
     * 이미지 교체 — 옛 S3 오브젝트는 지우지 않는다.
     *
     * <p>키가 uuid 기반이라 새 이미지는 언제나 새 키로 올라온다. 옛 오브젝트에는 1년
     * {@code immutable} 이 붙어 있어 지워도 엣지가 계속 서빙하므로, 지우는 쪽이
     * 오히려 반쪽짜리다.
     *
     * <p><b>그래서 고아 오브젝트가 쌓인다.</b> 정리할 수명주기 규칙은 아직 없다 —
     * 배너만의 문제가 아니라 공지 이미지·프로필도 같고(전부 교체 시 옛 키를 남긴다),
     * 규모가 작아 지금 막는 것이 없다. 버킷 비용이 눈에 띄면 프리픽스별 수명주기로
     * 한 번에 다룬다.
     */
    public void replaceImage(String imageKey) {
        this.imageKey = imageKey;
    }

    public void changeActive(boolean active) {
        this.active = active;
    }

    public void moveTo(short displayOrder) {
        this.displayOrder = displayOrder;
    }
}
