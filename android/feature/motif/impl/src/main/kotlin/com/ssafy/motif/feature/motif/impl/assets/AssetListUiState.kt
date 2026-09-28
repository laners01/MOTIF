package com.ssafy.motif.feature.motif.impl.assets

import androidx.compose.runtime.Immutable
import com.ssafy.motif.core.model.artist.Artist
import com.ssafy.motif.core.model.motif.Asset

internal sealed interface AssetListUiState {

    data object Loading : AssetListUiState

    @Immutable
    data class Success(
        val assets: List<Asset>,
        val artists: List<Artist>,
        val selectedArtistId: Long? = null,
    ) : AssetListUiState

    data class Error(
        val exception: Throwable,
    ) : AssetListUiState
}