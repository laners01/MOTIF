package com.ssafy.motif.feature.mypage.impl.mypage

import com.ssafy.motif.core.model.user.UserProfile

internal sealed interface ProfileUiState {

    data object Loading : ProfileUiState

    data class Success(
        val profile: UserProfile,
    ) : ProfileUiState

    data class Error(
        val exception: Throwable,
    ) : ProfileUiState
}