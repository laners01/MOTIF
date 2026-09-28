package com.ssafy.motif.feature.challenges.api.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data class ChallengeParticipationNavKey(
    val challengeId: Long? = null,
    val initialVideoId: Long? = null,
) : NavKey {

    init {
        require(
            challengeId != null ||
                    initialVideoId != null,
        )
    }
}