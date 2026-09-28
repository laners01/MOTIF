package com.ssafy.motif.core.model.shop

import com.ssafy.motif.core.model.artist.Artist
import com.ssafy.motif.core.model.motif.Asset

/**
 * 샵의 아티스트별 상품 섹션.
 *
 * @param hasPass 사용자가 이 아티스트의 구독권을 보유하고 있는지 여부.
 * @param assets 이 아티스트의 판매 에셋 전체.
 */
data class ShopArtistSection(
    val artist: Artist,
    val hasPass: Boolean,
    val assets: List<Asset>,
)
