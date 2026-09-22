package com.ssafy.motif.core.model.challenge

import com.ssafy.motif.core.model.artist.Artist

data class ChallengeParticipant(
    val artist: Artist,
    val participantMask: Int,
    val anchor: ChallengeAnchor?,
)