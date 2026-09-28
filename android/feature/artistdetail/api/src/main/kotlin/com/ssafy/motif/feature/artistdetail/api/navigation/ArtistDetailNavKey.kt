package com.ssafy.motif.feature.artistdetail.api.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data class ArtistDetailNavKey(
    val artistId: Long,
) : NavKey
