package com.ssafy.motif.core.model.shop

import com.ssafy.motif.core.model.artist.Artist

/**
 * 샵 상단에 노출되는 아티스트 구독권(PASS) 프로모션 배너.
 * 문구와 배경은 [imageUrl] 이미지에 포함되어 내려온다.
 */
data class PassBanner(
    val id: Long,
    val artist: Artist,
    val imageUrl: String,
)
