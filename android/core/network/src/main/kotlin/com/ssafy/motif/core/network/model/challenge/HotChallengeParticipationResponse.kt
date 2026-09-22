package com.ssafy.motif.core.network.model.challenge

import kotlinx.serialization.Serializable

@Serializable
data class HotChallengeParticipationResponse(
    val rank: Int,
    val videoSeq: Long,
    val videoUrl: String,
    val thumbnailUrl: String,
    val durationMs: Long,
    val song: ChallengeSongResponse,
    val author: HotChallengeParticipationAuthorResponse,
    val viewCount: Long,
)