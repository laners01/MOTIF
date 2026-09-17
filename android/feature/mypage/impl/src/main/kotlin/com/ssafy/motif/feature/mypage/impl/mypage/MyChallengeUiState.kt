package com.ssafy.motif.feature.mypage.impl.mypage

import com.ssafy.motif.core.model.mypage.MyChallenge

internal sealed interface MyChallengeUiState {
    data object Loading : MyChallengeUiState
    data class Success(
        val challenges: List<MyChallenge>,
        val hasNext: Boolean,
        val appendState: MyChallengeAppendUiState = MyChallengeAppendUiState.Idle,
    ) : MyChallengeUiState
    data class Error(val exception: Throwable) : MyChallengeUiState
}

internal sealed interface MyChallengeAppendUiState {
    data object Idle : MyChallengeAppendUiState
    data object Loading : MyChallengeAppendUiState
    data class Error(val exception: Throwable) : MyChallengeAppendUiState
}