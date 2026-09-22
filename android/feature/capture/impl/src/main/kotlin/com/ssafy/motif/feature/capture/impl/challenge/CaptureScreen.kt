package com.ssafy.motif.feature.capture.impl.challenge

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.source.MediaSource
import com.ssafy.motif.feature.capture.impl.R
import com.ssafy.motif.feature.capture.impl.challenge.model.CaptureStage
import com.ssafy.motif.feature.capture.impl.challenge.model.CaptureUiState
import com.ssafy.motif.feature.capture.impl.segmentation.rememberPersonSegmentationProcessor
import com.ssafy.motif.core.designsystem.R as DesignSystemR

@Composable
fun CaptureScreen(
    uiState: CaptureUiState,
    onBackClick: () -> Unit,
    onPositionClick: (Long) -> Unit,
    onStartChallengeClick: () -> Unit,
    onShootingReady: () -> Unit,
    onDurationReady: (Long) -> Unit,
    onRecordingProgress: (Long) -> Unit,
    onRecordingFinished: (Uri) -> Unit,
    onRetryReady: () -> Unit,
    onBrowseChallengeClick: () -> Unit,
    onError: (Throwable) -> Unit,
    modifier: Modifier = Modifier,
    mediaSourceFactory: MediaSource.Factory? = null,
) {
    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        when (uiState.stage) {
            CaptureStage.PositionSelection -> {
                CapturePositionSelectionScreen(
                    uiState = uiState,
                    onPositionClick = { artistId ->
                        if (artistId != uiState.originalArtistId) {
                            onPositionClick(artistId)
                        }
                    },
                    onStartChallengeClick = onStartChallengeClick,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            CaptureStage.Countdown,
            CaptureStage.Recording,
                -> {
                val segmentationProcessor =
                    rememberPersonSegmentationProcessor()

                CaptureShootingScreen(
                    uiState = uiState,
                    segmentationProcessor = segmentationProcessor,
                    onShootingReady = onShootingReady,
                    onDurationReady = onDurationReady,
                    onRecordingProgress = onRecordingProgress,
                    onRecordingFinished = onRecordingFinished,
                    onRetryReady = onRetryReady,
                    onError = onError,
                    mediaSourceFactory = mediaSourceFactory,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            CaptureStage.Processing -> {
                CaptureProcessingScreen(
                    previewImageUrl = uiState.selectedPosition
                        ?.previewImageUrl
                        .orEmpty(),
                    onBrowseChallengeClick = onBrowseChallengeClick,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        CaptureBackButton(
            onClick = onBackClick,
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing,
                )
                .padding(
                    top = 8.dp,
                    start = 12.dp,
                ),
        )
    }
}

@Composable
private fun CaptureBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource =
        remember {
            MutableInteractionSource()
        }

    Box(
        modifier = modifier
            .size(48.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(
                DesignSystemR.drawable.ic_arrow_left_24,
            ),
            contentDescription = stringResource(
                R.string.capture_back,
            ),
            colorFilter = ColorFilter.tint(
                Color.White,
            ),
            modifier = Modifier.size(32.dp),
        )
    }
}