package com.ssafy.motif.feature.mypage.impl.mypage

import androidx.compose.runtime.Immutable

enum class MyPageTab(val label: String){
    Challenge("Challenges"),
    Artist("Artists"),
    Liked("Liked")
}

@Immutable
internal data class MyPageUiState(
    val profileState: ProfileUiState = ProfileUiState.Loading,
    val selectedTab: MyPageTab = MyPageTab.Challenge,
    val challengeState: MyChallengeUiState = MyChallengeUiState.Loading,
    val artistState: MyArtistUiState = MyArtistUiState.Loading,
    val flippedArtistId: Long? = null,
    val nicknameEdit: NicknameEditUiState? = null,
)

internal data class NicknameEditUiState(
    val value: String,
    val isValid: Boolean,
)