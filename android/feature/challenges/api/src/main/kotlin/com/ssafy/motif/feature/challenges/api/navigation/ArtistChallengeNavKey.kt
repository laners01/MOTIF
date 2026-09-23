package com.ssafy.motif.feature.challenges.api.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data class ArtistChallengeNavKey(
    val initialChallengeId: Long,
) : NavKey