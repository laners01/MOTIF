package com.ssafy.motif.feature.mypage.impl.mypage

import com.ssafy.motif.core.model.mypage.LikedMotif

internal sealed interface MyLikedUiState {
    data object Loading : MyLikedUiState

    data class Success(
        val artists: List<LikedMotif>,
        val hasNext:Boolean,
        val appendState: MyLikedAppendUiState = MyLikedAppendUiState.Idle,
    ) : MyLikedUiState

    data class Error(
        val exception: Throwable,
    ) : MyLikedUiState
}

internal sealed interface MyLikedAppendUiState {
    data object Idle : MyLikedAppendUiState
    data object Loading : MyLikedAppendUiState
    data class Error(val exception: Throwable) : MyLikedAppendUiState
}