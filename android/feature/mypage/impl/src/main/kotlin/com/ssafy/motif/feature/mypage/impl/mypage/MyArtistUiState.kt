package com.ssafy.motif.feature.mypage.impl.mypage

import com.ssafy.motif.core.model.user.UserSubscription

internal sealed interface MyArtistUiState {

    data object Idle : MyArtistUiState

    data object Loading : MyArtistUiState

    data class Success(
        val artists: List<UserSubscription>,
    ) : MyArtistUiState

    data class Error(
        val exception: Throwable,
    ) : MyArtistUiState
}