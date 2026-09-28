package com.ssafy.motif.coreapi.admin.banner.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * §3.11.2 등록 요청.
 *
 * @param displayOrder 생략하면 {@code MAX+1} — 맨 뒤에 붙인다. 지정했는데 이미 쓰는
 *                     순서면 409 {@code BANNER_ORDER_DUPLICATE}
 */
public record BannerCreateRequest(
        @NotBlank(message = "imageKey 는 필수다")
        String imageKey,

        @Min(value = 0, message = "displayOrder 는 0 이상이다")
        Short displayOrder) {
}
