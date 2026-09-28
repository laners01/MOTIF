package com.ssafy.motif.feature.capture.impl.challenge.result

import com.ssafy.motif.core.model.composite.ChallengeCompositeJob

data class ChallengeCompositeResultUiState(
    val jobUuid: String = "",
    val compositeJob: ChallengeCompositeJob? = null,
    val description: String = "",
    val isPublic: Boolean = true,
    val commentEnabled: Boolean = true,
    val dialog: CompositeResultDialog =
        CompositeResultDialog.None,
    val isLoading: Boolean = false,
    val isPublishing: Boolean = false,
    val error: Throwable? = null,
)

enum class CompositeResultDialog {
    None,
    Upload,
    Delete,
}

sealed interface ChallengeCompositeResultEffect {

    data class Published(
        val videoId: Long,
    ) : ChallengeCompositeResultEffect

    data object Close :
        ChallengeCompositeResultEffect

    data class Error(
        val throwable: Throwable,
    ) : ChallengeCompositeResultEffect
}