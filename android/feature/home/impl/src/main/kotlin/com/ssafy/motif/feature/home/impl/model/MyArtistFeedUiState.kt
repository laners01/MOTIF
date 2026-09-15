package com.ssafy.motif.feature.home.impl.model

internal sealed interface MyArtistFeedUiState {

    data object Loading : MyArtistFeedUiState

    data class Success(
        val items: List<MyArtistFeedItem>,
        val hasNext: Boolean,
        val appendState: FeedAppendUiState = FeedAppendUiState.Idle,
    ) : MyArtistFeedUiState

    data class Error(
        val exception: Throwable,
    ) : MyArtistFeedUiState
}