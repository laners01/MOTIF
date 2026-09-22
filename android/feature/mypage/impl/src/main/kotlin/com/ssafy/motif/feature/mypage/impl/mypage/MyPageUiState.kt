package com.ssafy.motif.feature.mypage.impl.mypage

import androidx.compose.runtime.Immutable
import com.ssafy.motif.core.model.motif.Motif

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
    val likedState: MyLikedUiState = MyLikedUiState.Loading,
    val likedFocusedIndex: Int = 0,
    val flippedArtistId: Long? = null,
    val nicknameEdit: NicknameEditUiState? = null, // null이면 대표 모티프 미설정
    val representativeMotif: Motif? = null,
)

internal data class NicknameEditUiState(
    val value: String,
    val isValid: Boolean,
)

