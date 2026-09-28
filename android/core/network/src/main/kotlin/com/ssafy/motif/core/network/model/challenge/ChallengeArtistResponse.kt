package com.ssafy.motif.core.network.model.challenge

import kotlinx.serialization.Serializable

@Serializable
data class ChallengeArtistResponse(
    val artistSeq: Long,
    val name: String,
    val profileImageUrl: String?,
)