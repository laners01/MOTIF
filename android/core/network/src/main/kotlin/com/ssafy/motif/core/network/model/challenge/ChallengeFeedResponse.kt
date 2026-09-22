package com.ssafy.motif.core.network.model.challenge

import kotlinx.serialization.Serializable

@Serializable
data class ChallengeFeedResponse(
    val list: List<ChallengeFeedItemResponse>,
    val prevCursor: String?,
    val nextCursor: String?,
    val hasPrevious: Boolean,
    val hasNext: Boolean,
)