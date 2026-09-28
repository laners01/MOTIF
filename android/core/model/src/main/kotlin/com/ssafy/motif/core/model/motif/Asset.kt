package com.ssafy.motif.core.model.motif

import com.ssafy.motif.core.model.artist.Artist

data class Asset(
    val id: Long,
    val thumbnailUrl: String,
    val title: String,
    val artist: Artist,
    val availability: AssetAvailability,
    val description: String = "",
    val durationMs: Long = 0L,
    val type: AssetType = AssetType.NORMAL,
    val isPremium: Boolean = false,
    val price: Int = 0,
    val currency: String = "KRW",
    val previewVideoUrl: String = "",
    val purchasable: Boolean = false,
) {
    val isAvailable: Boolean
        get() =
            availability !=
                    AssetAvailability.LOCKED
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