package com.ssafy.motif.feature.mypage.impl.mypage

import com.ssafy.motif.core.model.mypage.UserProfile

internal sealed interface MyPageUiState {

    data object Loading: MyPageUiState

    data class Success(
        val profile: UserProfile,
    ): MyPageUiState

    data class Error(
        val exception: Throwable,
    ): MyPageUiState
}