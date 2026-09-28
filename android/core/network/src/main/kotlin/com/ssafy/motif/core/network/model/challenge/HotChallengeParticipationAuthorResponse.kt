package com.ssafy.motif.core.network.model.challenge

import kotlinx.serialization.Serializable

@Serializable
data class HotChallengeParticipationAuthorResponse(
    val userSeq: Long,
    val nickname: String,
)