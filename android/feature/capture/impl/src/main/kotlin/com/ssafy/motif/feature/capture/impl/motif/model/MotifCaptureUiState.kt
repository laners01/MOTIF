package com.ssafy.motif.feature.capture.impl.motif.model

import com.ssafy.motif.core.model.motif.Asset

data class MotifCaptureUiState(
    val assetId: Long = 0L,
    val asset: Asset? = null,
    val recommendedAssets: List<Asset> = emptyList(),
    val stage: MotifCaptureStage = MotifCaptureStage.Ready,
    val countdown: Int = DEFAULT_COUNTDOWN,
    val elapsedTimeMs: Long = 0L,
    val processingState: MotifCaptureProcessingState =
        MotifCaptureProcessingState.Idle,
    val uploadProgress: Int = 0,
    val jobUuid: String? = null,
    val isLoading: Boolean = false,
) {

    val durationMs: Long
        get() = asset?.durationMs ?: 0L

    val previewImageUrl: String
        get() = asset?.thumbnailUrl.orEmpty()

    val isTransferInProgress: Boolean
        get() =
            processingState ==
                MotifCaptureProcessingState.PreparingUpload ||
                processingState ==
                MotifCaptureProcessingState.Uploading ||
                processingState ==
                MotifCaptureProcessingState.RequestingComposite

    private companion object {
        const val DEFAULT_COUNTDOWN = 5
    }
}
