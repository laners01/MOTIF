package com.ssafy.motif.core.model.motif

import com.ssafy.motif.core.model.artist.Artist

data class Asset(
    val id: Long,
    val thumbnailUrl: String,
    val title: String,
    val artist: Artist,
    val availability: AssetAvailability,
    /** 프리미엄 에셋 여부. 표시 방식은 디자인 확정 후 반영한다. */
    val isPremium: Boolean = false,
    /** 개별 구매 가격 (원). 기본값은 아직 가격 정보를 내려주지 않는 목록 응답을 위한 것이다. */
    val price: Int = 0,
    /** 상품 상세에서 재생하는 미리보기 영상. 목록 응답에는 없을 수 있다. */
    val previewVideoUrl: String = "",
) {
    /** 사용자가 지금 바로 사용할 수 있는 에셋인지 여부. */
    val isAvailable: Boolean
        get() = availability != AssetAvailability.LOCKED
}

enum class AssetAvailability {
    /** 개별 구매한 에셋. */
    PURCHASED,

    /** 보유한 구독권으로 이용 가능한 에셋. */
    SUBSCRIPTION,

    /** 구매하지 않았고 구독권으로도 이용할 수 없는 에셋. */
    LOCKED,
}
