package com.ssafy.motif.feature.onboarding.impl.nicknameinput

import androidx.compose.runtime.Immutable
import com.ssafy.motif.feature.onboarding.impl.artistselect.ArtistUiModel

@Immutable
data class NicknameInputUiState(
    val nickname: String = "",
    val likedArtists: List<ArtistUiModel> = emptyList(),
){
    val canProceed: Boolean
        get() = nickname.isNotEmpty()

    companion object{
        const val MAX_NICKNAME_LENGTH = 20
    }
}