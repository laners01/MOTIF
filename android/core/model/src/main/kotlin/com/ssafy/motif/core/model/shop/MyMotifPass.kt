package com.ssafy.motif.core.model.shop

import com.ssafy.motif.core.model.artist.Artist

data class MyMotifPass(
    val artistId: Long,
    val artist: Artist,
    val status: MotifPassStatus,
    val startedAt: Long,
    val expiresAt: Long,
    val durationMonths: Int,
    val canceledAt: Long?,
)

enum class MotifPassStatus {
    ACTIVE,
    EXPIRED,
    REFUNDED,
}