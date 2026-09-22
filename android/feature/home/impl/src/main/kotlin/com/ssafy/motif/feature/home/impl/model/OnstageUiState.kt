package com.ssafy.motif.feature.home.impl.model

import com.ssafy.motif.core.model.challenge.ChallengeParticipationSummary

internal sealed interface OnstageUiState {

    data object Loading : OnstageUiState

    data class Success(
        val items: List<ChallengeParticipationSummary>,
    ) : OnstageUiState

    data class Error(
        val exception: Throwable,
    ) : OnstageUiState
}