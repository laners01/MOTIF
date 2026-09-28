package com.ssafy.motif.feature.mypage.impl.mypage

internal sealed interface MyLikedUiState {

    data object Idle : MyLikedUiState

    data object Loading : MyLikedUiState

    data class Success(
        val challenges: List<LikedChallengeUiModel>,
        val nextCursor: String?,
        val hasNext: Boolean,
        val appendState: MyLikedAppendUiState = MyLikedAppendUiState.Idle,
    ) : MyLikedUiState

    data class Error(
        val exception: Throwable,
    ) : MyLikedUiState
}

internal sealed interface MyLikedAppendUiState {

    data object Idle : MyLikedAppendUiState

    data object Loading : MyLikedAppendUiState

    data class Error(
        val exception: Throwable,
    ) : MyLikedAppendUiState
}