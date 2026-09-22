package com.ssafy.motif.core.network.model.challenge

import kotlinx.serialization.Serializable

@Serializable
data class ChallengeSongResponse(
    val title: String,
    val singerName: String,
)