package com.ssafy.motif.core.model.shop

import com.ssafy.motif.core.model.home.Artist
import com.ssafy.motif.core.model.motif.Asset

/**
 * 아티스트별 모티프 상품 상세.
 *
 * @param introduction 아티스트 소개 문구 (2줄).
 * @param assets 판매 에셋 전체. 각 항목은 가격과 미리보기 영상을 포함한다.
 * @param pass 구독권 상품.
 * @param hasPass 사용자가 이 아티스트의 구독권을 보유하고 있는지 여부.
 */
data class ShopArtistDetail(
    val artist: Artist,
    val introduction: String,
    val assets: List<Asset>,
    val pass: ArtistPass,
    val hasPass: Boolean,
)
