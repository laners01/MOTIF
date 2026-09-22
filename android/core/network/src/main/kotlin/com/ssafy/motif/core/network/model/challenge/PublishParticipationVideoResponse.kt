package com.ssafy.motif.core.network.model.challenge

import kotlinx.serialization.Serializable

@Serializable
data class PublishParticipationVideoResponse(
    val videoSeq: Long,
)