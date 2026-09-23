package com.ssafy.motif.feature.challenges.impl.participation

import androidx.compose.runtime.Immutable
import com.ssafy.motif.core.model.challenge.ChallengeParticipation

@Immutable
internal sealed interface ChallengeParticipationUiState {

    data object Loading : ChallengeParticipationUiState

    data class Success(
        val challengeId: Long,
        val participations: List<ChallengeParticipation>,
        val initialPage: Int,
        val nextCursor: String?,
        val hasNext: Boolean,
        val appendState: ChallengeParticipationAppendUiState =
            ChallengeParticipationAppendUiState.Idle,
    ) : ChallengeParticipationUiState

    data class Empty(
        val challengeId: Long,
    ) : ChallengeParticipationUiState

    data class Error(
        val exception: Throwable,
    ) : ChallengeParticipationUiState
}

@Immutable
internal sealed interface ChallengeParticipationAppendUiState {

    data object Idle : ChallengeParticipationAppendUiState

    data object Loading : ChallengeParticipationAppendUiState

    data class Error(
        val exception: Throwable,
    ) : ChallengeParticipationAppendUiState
}