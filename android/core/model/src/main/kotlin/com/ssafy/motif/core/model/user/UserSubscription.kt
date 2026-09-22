package com.ssafy.motif.core.model.user

import com.ssafy.motif.core.model.artist.Artist

data class UserSubscription(
    val artist: Artist,
    val passActive: Boolean,
    val passExpiresAt: Long?,
    val subscribedDays: Int,
    val challengeCount: Long,
    val purchasedAssetCount: Long,
    val totalAssetCount: Long,
)