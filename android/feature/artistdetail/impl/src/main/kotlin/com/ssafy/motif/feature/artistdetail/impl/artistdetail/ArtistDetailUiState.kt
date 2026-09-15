package com.ssafy.motif.feature.artistdetail.impl.artistdetail

import androidx.compose.runtime.Immutable
import com.ssafy.motif.core.model.artistdetail.ArtistDetail

@Immutable
data class ArtistDetailUiState(
    val artistId: Long = 0L,
    val artistDetail: ArtistDetail? = null,
)
