package com.ssafy.motif.feature.home.impl.model

internal sealed interface FeedAppendUiState {

    data object Idle : FeedAppendUiState

    data object Loading : FeedAppendUiState

    data class Error(
        val exception: Throwable,
    ) : FeedAppendUiState
}