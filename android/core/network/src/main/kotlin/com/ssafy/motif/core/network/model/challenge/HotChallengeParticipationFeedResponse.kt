package com.ssafy.motif.core.network.model.challenge

import kotlinx.serialization.Serializable

@Serializable
data class HotChallengeParticipationFeedResponse(
    val list: List<HotChallengeParticipationResponse>,
    val prevCursor: String?,
    val nextCursor: String?,
    val hasPrevious: Boolean,
    val hasNext: Boolean,
)