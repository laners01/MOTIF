package com.ssafy.motif.coreapi.domain.shop.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 숍 공용 배너 항목 (API 명세 §11.11).
 *
 * <p><b>어드민 {@code BannerItem}(§3.11.1)과 합치지 않는다.</b> 저쪽은 운영 화면이라
 * {@code imageKey}·{@code isActive}·{@code regDttm} 을 함께 주는데, 팬 응답에 섞이면
 * 내려둔 배너가 있다는 사실과 S3 키가 그대로 드러난다. 같은 테이블을 읽어도 집합과 칸이
 * 다르다.
 *
 * <p><b>클릭 액션이 없다.</b> 필요해지면 {@code linkType}·{@code linkTarget} 을 더한다 —
 * 컬럼도 도입하지 않은 상태다(changeSet 0011-01 주석).
 */
@Schema(description = "숍 공용 배너 항목 (§11.11)")
public record ShopBannerItem(

        @Schema(description = "배너 식별자", example = "1")
        Integer bannerSeq,

        @Schema(description = "배너 이미지 URL — 공개 콘텐츠라 **무서명** CDN 주소다")
        String imageUrl,

        @Schema(description = "노출 순서. 오름차순으로 그린다", example = "0")
        short displayOrder) {
}
