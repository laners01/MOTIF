package com.ssafy.motif.feature.challenges.impl.artist

import androidx.compose.runtime.Immutable
import com.ssafy.motif.core.model.challenge.Challenge

@Immutable
internal sealed interface ArtistChallengeUiState {

    data object Loading : ArtistChallengeUiState

    data class Success(
        val challenges: List<Challenge>,
        val nextCursor: String?,
        val hasNext: Boolean,
        val appendState: ArtistChallengeAppendUiState =
            ArtistChallengeAppendUiState.Idle,
    ) : ArtistChallengeUiState

    data class Error(
        val exception: Throwable,
    ) : ArtistChallengeUiState
}

@Immutable
internal sealed interface ArtistChallengeAppendUiState {

    data object Idle : ArtistChallengeAppendUiState

    data object Loading : ArtistChallengeAppendUiState

    data class Error(
        val exception: Throwable,
    ) : ArtistChallengeAppendUiState
}