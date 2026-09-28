package com.ssafy.motif.core.network.model.challenge

import kotlinx.serialization.Serializable

@Serializable
data class ChallengeAnchorResponse(
    val x: Float,
    val y: Float,
    val r: Float,
)