package com.ssafy.motif.core.network.model.challenge

import kotlinx.serialization.Serializable

@Serializable
data class ParticipationVideoAuthorResponse(
    val userSeq: Long,
    val nickname: String,
    val profileImageUrl: String?,
)