package com.ssafy.motif.feature.artistdetail.impl.artistdetail

import com.ssafy.motif.core.model.challenge.ChallengeSummary

internal sealed interface ChallengeUiState {

    data object Loading : ChallengeUiState

    data class Success(
        val challenges: List<ChallengeSummary>,
        val hasNext: Boolean,
        val appendState: ChallengeAppendUiState =
            ChallengeAppendUiState.Idle,
    ) : ChallengeUiState

    data class Error(
        val exception: Throwable,
    ) : ChallengeUiState
}

internal sealed interface ChallengeAppendUiState {
    data object Idle : ChallengeAppendUiState
    data object Loading : ChallengeAppendUiState
    data class Error(val exception: Throwable) : ChallengeAppendUiState
}