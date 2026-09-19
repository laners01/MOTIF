package com.ssafy.motif.core.model.motif

import com.ssafy.motif.core.model.home.Artist

data class Asset(
    val id: Long,
    val thumbnailUrl: String,
    val title: String,
    val artist: Artist,
    val availability: AssetAvailability,
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
