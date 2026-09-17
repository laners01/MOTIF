package com.ssafy.motif.feature.mypage.impl.mypage

import com.ssafy.motif.core.model.mypage.SubscribedArtist

internal sealed interface MyArtistUiState {
    data object Loading : MyArtistUiState

    data class Success(
        val artists: List<SubscribedArtist>,
    ) : MyArtistUiState

    data class Error(
        val exception: Throwable,
    ) : MyArtistUiState
}