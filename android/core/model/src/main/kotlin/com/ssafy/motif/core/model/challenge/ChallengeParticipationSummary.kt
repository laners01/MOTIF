package com.ssafy.motif.core.model.challenge

data class ChallengeParticipationSummary(
    val id: Long,
    val rank: Int,
    val videoUrl: String,
    val thumbnailUrl: String,
    val song: ChallengeSong,
)