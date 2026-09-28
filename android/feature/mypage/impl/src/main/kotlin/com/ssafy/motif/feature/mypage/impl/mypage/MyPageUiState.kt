package com.ssafy.motif.feature.mypage.impl.mypage

import androidx.compose.runtime.Immutable
import com.ssafy.motif.core.model.user.RepresentativeMotif

@Immutable
internal data class MyPageUiState(
    val profileState: ProfileUiState = ProfileUiState.Loading,
    val representativeMotif: RepresentativeMotif? = null,
    val challengeState: MyChallengeUiState = MyChallengeUiState.Loading,
    val artistState: MyArtistUiState = MyArtistUiState.Idle,
    val likedState: MyLikedUiState = MyLikedUiState.Idle,
    val selectedTab: MyPageTab = MyPageTab.Challenge,
    val flippedArtistId: Long? = null,
    val likedFocusedIndex: Int = 0,
    val nicknameEdit: NicknameEditUiState? = null,
    val isProfileImageUpdating: Boolean = false,
    val profileImageErrorMessage: String? = null,
)

@Immutable
internal data class NicknameEditUiState(
    val value: String,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)

internal enum class MyPageTab(
    val label: String,
) {
    Challenge("Challenge"),
    Artist("Artist"),
    Liked("Liked"),
}