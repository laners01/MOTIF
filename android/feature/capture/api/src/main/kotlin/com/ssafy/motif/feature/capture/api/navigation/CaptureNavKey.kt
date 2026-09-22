package com.ssafy.motif.feature.capture.api.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data class CaptureNavKey(
    val challengeId: Long,
    val challengeThumbnailUrl: String,
) : NavKey