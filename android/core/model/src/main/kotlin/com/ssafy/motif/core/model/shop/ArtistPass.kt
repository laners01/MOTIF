package com.ssafy.motif.core.model.shop

data class ArtistPass(
    val artistId: Long,
    val bannerImageUrl: String,
    val headline: String,
    val description: String,
    val plans: List<PassPlan>,
    val isActive: Boolean = false,
    val expiresAt: Long? = null,
)