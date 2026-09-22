package com.ssafy.motif.core.network.model.challenge

import kotlinx.serialization.Serializable

@Serializable
data class ChallengeParticipantResponse(
    val artistSeq: Long,
    val name: String,
    val participantMask: Int,
    val profileImageUrl: String?,
    val anchor: ChallengeAnchorResponse?,
)