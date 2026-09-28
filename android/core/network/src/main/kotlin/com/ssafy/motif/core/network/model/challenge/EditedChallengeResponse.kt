package com.ssafy.motif.core.network.model.challenge

import kotlinx.serialization.Serializable

@Serializable
data class EditedChallengeResponse(
    val participantMask: Int,
    val previewImageUrl: String,
)