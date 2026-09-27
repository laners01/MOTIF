package com.ssafy.motif.core.model.shop

import com.ssafy.motif.core.model.artist.Artist

/**
 * 숍 상단 공용 배너.
 *
 * 서버의 /api/v1/shop/banners 응답에는 artist 정보나 클릭 액션이 없다.
 *
 * [artist]는 기존 PassBannerPager 호환을 위해 임시 유지하며
 * 실제 네비게이션 대상으로 사용하지 않는다.
 */
data class PassBanner(
    val id: Long,
    val artist: Artist = Artist(
        id = 0L,
        name = "",
        profileImageUrl = "",
        groupName = null,
    ),
    val imageUrl: String,
    val displayOrder: Int = 0,
)