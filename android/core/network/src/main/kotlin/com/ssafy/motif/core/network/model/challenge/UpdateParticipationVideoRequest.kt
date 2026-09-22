package com.ssafy.motif.core.network.model.challenge

import kotlinx.serialization.Serializable

@Serializable
data class UpdateParticipationVideoRequest(
    val description: String? = null,
    val visibility: VideoVisibility? = null,
    val commentEnabled: Boolean? = null,
)