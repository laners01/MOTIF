package com.ssafy.motif.core.model.shop

/**
 * 아티스트 구독권(PASS) 상품.
 *
 * @param bannerImageUrl 문구·배경이 포함된 배너 이미지. [PassBanner]와 같은 이미지를 쓴다.
 * @param headline PASS 안내 헤드라인 (예: 좋아하는 아티스트의 MOTIF를 더 자유롭게 이용해보세요.).
 * @param description PASS 안내 설명 문구.
 * @param plans 결제 플랜. 기간 오름차순.
 */
data class ArtistPass(
    val artistId: Long,
    val bannerImageUrl: String,
    val headline: String,
    val description: String,
    val plans: List<PassPlan>,
)
