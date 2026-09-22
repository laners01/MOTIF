package com.ssafy.motif.core.model.challenge

data class ChallengeDetail(
    val challenge: Challenge,
    val description: String,
    val participants: List<ChallengeParticipant>,
    val editedChallenges: List<EditedChallenge>,
)