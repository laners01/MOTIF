package com.ssafy.motif.feature.home.impl.model

import com.ssafy.motif.core.model.challenge.ChallengeRank

internal sealed interface FanRadarUiState {

    data object Loading : FanRadarUiState

    data class Success(
        val artists: List<ChallengeRank>,
    ) : FanRadarUiState

    data class Error(
        val exception: Throwable,
    ) : FanRadarUiState
}