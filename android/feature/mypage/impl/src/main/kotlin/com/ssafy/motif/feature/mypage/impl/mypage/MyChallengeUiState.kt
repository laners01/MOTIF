package com.ssafy.motif.feature.mypage.impl.mypage

import com.ssafy.motif.core.model.user.UserVideo

internal sealed interface MyChallengeUiState {

    data object Loading : MyChallengeUiState

    data class Success(
        val challenges: List<UserVideo>,
    ) : MyChallengeUiState

    data class Error(
        val exception: Throwable,
    ) : MyChallengeUiState
}