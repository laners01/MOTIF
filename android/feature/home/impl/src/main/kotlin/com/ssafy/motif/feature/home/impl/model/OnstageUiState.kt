package com.ssafy.motif.feature.home.impl.model

import com.ssafy.motif.core.model.home.Onstage

internal sealed interface OnstageUiState {

    data object Loading : OnstageUiState

    data class Success(
        val items: List<Onstage>,
    ) : OnstageUiState

    data class Error(
        val exception: Throwable,
    ) : OnstageUiState
}