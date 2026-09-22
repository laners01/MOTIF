package com.ssafy.motif.feature.home.impl.model

import com.ssafy.motif.core.model.artist.RankedArtist

internal sealed interface FanRadarUiState {

    data object Loading : FanRadarUiState

    data class Success(
        val artists: List<RankedArtist>,
    ) : FanRadarUiState

    data class Error(
        val exception: Throwable,
    ) : FanRadarUiState
}