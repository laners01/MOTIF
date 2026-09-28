package com.ssafy.motif.core.network.model.challenge

import kotlinx.serialization.Serializable

@Serializable
data class PublishParticipationVideoRequest(
    val jobUuid: String,
    val description: String,
    val visibility: VideoVisibility,
    val commentEnabled: Boolean,
)