package com.ssafy.motif.coreapi.admin.banner.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * §3.11.4 전체 재정렬 — 배열 순서가 곧 {@code display_order} 0..n−1 이다.
 *
 * <p><b>전량이어야 한다.</b> 일부만 보내면 보내지 않은 행의 순서가 어디에 놓일지
 * 요청만 봐서는 정해지지 않는다 — 누락·미존재·중복은 400 이다.
 */
public record BannerOrderRequest(
        @NotEmpty(message = "orderedBannerSeqs 는 비어 있을 수 없다")
        List<Integer> orderedBannerSeqs) {
}
