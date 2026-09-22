package com.ssafy.motif.feature.capture.impl.challenge.model

import com.ssafy.motif.core.model.challenge.CapturePosition

data class CaptureUiState(
    val originalArtistId: Long = 0L,
    val positions: List<CapturePosition> = emptyList(),
    val selectedPositionArtistId: Long? = null,
    val stage: CaptureStage = CaptureStage.PositionSelection,
    val countdown: Int = 5,
    val elapsedTimeMs: Long = 0L,
    val durationMs: Long = 0L,
) {
    val selectedPosition: CapturePosition?
        get() =
            positions.firstOrNull { position ->
                position.artist.id == selectedPositionArtistId &&
                        position.artist.id != originalArtistId
            }
}