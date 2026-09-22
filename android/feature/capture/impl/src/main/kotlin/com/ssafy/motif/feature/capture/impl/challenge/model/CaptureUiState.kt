package com.ssafy.motif.feature.capture.impl.challenge.model

import com.ssafy.motif.core.model.challenge.CapturePosition

data class CaptureUiState(
    val challengeId: Long = 0L,
    val challengeThumbnailUrl: String = "",
    val positions: List<CapturePosition> = emptyList(),
    val selectedParticipantId: Long? = null,
    val stage: CaptureStage =
        CaptureStage.PositionSelection,
    val countdown: Int = DEFAULT_COUNTDOWN,
    val elapsedTimeMs: Long = 0L,
    val durationMs: Long = 0L,
    val guideVideoUrl: String = "",
    val jobUuid: String? = null,
    val isLoading: Boolean = false,
    val isPreparingShooting: Boolean = false,
    val isSubmitting: Boolean = false,
) {

    val selectedPosition: CapturePosition?
        get() =
            positions.firstOrNull { position ->
                position.id ==
                        selectedParticipantId
            }

    val previewImageUrl: String
        get() =
            selectedPosition
                ?.previewImageUrl
                ?.takeIf { url ->
                    url.isNotBlank()
                }
                ?: challengeThumbnailUrl

    val canStartChallenge: Boolean
        get() =
            selectedPosition?.isAvailable == true &&
                    !isPreparingShooting

    private companion object {
        const val DEFAULT_COUNTDOWN = 5
    }
}