package com.ssafy.motif.feature.capture.api.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data class ChallengeCompositeResultNavKey(
    val jobUuid: String,
) : NavKey