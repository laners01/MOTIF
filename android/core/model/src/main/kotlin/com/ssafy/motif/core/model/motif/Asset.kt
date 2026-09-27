package com.ssafy.motif.core.model.motif

import com.ssafy.motif.core.model.artist.Artist

data class Asset(
    val id: Long,
    val thumbnailUrl: String,
    val title: String,
    val artist: Artist,
    val availability: AssetAvailability,

    /**
     * 상세/목록 공통 정보.
     * API에 없는 화면에서는 기본값을 사용한다.
     */
    val description: String? = null,
    val durationMs: Long = 0L,
    val type: AssetType = AssetType.NORMAL,

    /** 기존 UI 호환용. */
    val isPremium: Boolean = false,

    /**
     * 개별 구매 가격.
     * 목록/샵 응답에는 가격이 없으므로 기본값은 0.
     */
    val price: Int = 0,
    val currency: String = "KRW",

    /**
     * 상세 API에서만 제공된다.
     */
    val previewVideoUrl: String = "",

    /**
     * 상세 API에서 내려주는 실제 구매 가능 여부.
     */
    val purchasable: Boolean = false,
) {
    val isAvailable: Boolean
        get() = availability != AssetAvailability.LOCKED
}

enum class AssetAvailability {
    PURCHASED,
    SUBSCRIPTION,
    LOCKED,
}

enum class AssetType {
    NORMAL,
    SECRET,
    PREMIUM,
}