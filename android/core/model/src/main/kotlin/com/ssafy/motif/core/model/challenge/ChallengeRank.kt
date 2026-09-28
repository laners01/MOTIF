package com.ssafy.motif.core.model.challenge

import com.ssafy.motif.core.model.artist.Artist

data class ChallengeRank(
    val challengeId: Long,
    val rank: Long,
    val artist: Artist,
    val song: ChallengeSong,
    val rankDelta: Long,
)