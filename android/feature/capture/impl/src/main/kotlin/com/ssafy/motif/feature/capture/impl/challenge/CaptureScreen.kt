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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.source.MediaSource
import com.ssafy.motif.core.designsystem.component.LoadingWheel
import com.ssafy.motif.feature.capture.impl.R
import com.ssafy.motif.feature.capture.impl.challenge.model.CaptureStage
import com.ssafy.motif.feature.capture.impl.challenge.model.CaptureUiEffect
import com.ssafy.motif.feature.capture.impl.challenge.model.CaptureUiState
import com.ssafy.motif.feature.capture.impl.segmentation.rememberPersonSegmentationProcessor
import com.ssafy.motif.core.designsystem.R as DesignSystemR

@Composable
internal fun CaptureScreen(
    challengeId: Long,
    challengeThumbnailUrl: String,
    onBackClick: () -> Unit,
    onBrowseChallengeClick: () -> Unit,
    onError: (Throwable) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CaptureViewModel = hiltViewModel(),
) {
    val uiState by
    viewModel.uiState
        .collectAsStateWithLifecycle()

    LaunchedEffect(
        challengeId,
        challengeThumbnailUrl,
    ) {
        viewModel.initialize(
            challengeId =
                challengeId,
            challengeThumbnailUrl =
                challengeThumbnailUrl,
        )
    }

    LaunchedEffect(
        viewModel,
    ) {
        viewModel.uiEffect
            .collect { effect ->
                when (effect) {
                    is CaptureUiEffect.Error -> {
                        onError(
                            effect.throwable,
                        )
                    }
                }
            }
    }

    CaptureScreen(
        uiState =
            uiState,
        onBackClick =
            onBackClick,
        onPositionClick =
            viewModel::selectPosition,
        onStartChallengeClick =
            viewModel::startChallenge,
        onShootingReady =
            viewModel::onShootingReady,
        onRecordingProgress =
            viewModel::onRecordingProgress,
        onRecordingFinished =
            viewModel::onRecordingFinished,
        onRetryReady =
            viewModel::onRetryReady,
        onBrowseChallengeClick =
            onBrowseChallengeClick,
        onError =
            viewModel::onError,
        modifier =
            modifier,
    )
}

@Composable
internal fun CaptureScreen(
    uiState: CaptureUiState,
    onBackClick: () -> Unit,
    onPositionClick: (Long) -> Unit,
    onStartChallengeClick: () -> Unit,
    onShootingReady: () -> Unit,
    onRecordingProgress: (Long) -> Unit,
    onRecordingFinished: (Uri) -> Unit,
    onRetryReady: () -> Unit,
    onBrowseChallengeClick: () -> Unit,
    onError: (Throwable) -> Unit,
    modifier: Modifier = Modifier,
    mediaSourceFactory: MediaSource.Factory? = null,
) {
    Box(
        modifier =
            modifier.fillMaxSize(),
    ) {
        when (
            uiState.stage
        ) {
            CaptureStage.PositionSelection -> {
                CapturePositionSelectionScreen(
                    uiState =
                        uiState,
                    onPositionClick =
                        onPositionClick,
                    onStartChallengeClick =
                        onStartChallengeClick,
                    modifier =
                        Modifier.fillMaxSize(),
                )
            }

            CaptureStage.Countdown,
            CaptureStage.Recording,
                -> {
                val segmentationProcessor =
                    rememberPersonSegmentationProcessor()

                CaptureShootingScreen(
                    uiState =
                        uiState,
                    segmentationProcessor =
                        segmentationProcessor,
                    onShootingReady =
                        onShootingReady,
                    onRecordingProgress =
                        onRecordingProgress,
                    onRecordingFinished =
                        onRecordingFinished,
                    onRetryReady =
                        onRetryReady,
                    onError =
                        onError,
                    mediaSourceFactory =
                        mediaSourceFactory,
                    modifier =
                        Modifier.fillMaxSize(),
                )
            }

            CaptureStage.Processing -> {
                CaptureProcessingScreen(
                    previewImageUrl =
                        uiState.previewImageUrl,
                    browseEnabled =
                        !uiState.isSubmitting,
                    onBrowseChallengeClick =
                        onBrowseChallengeClick,
                    modifier =
                        Modifier.fillMaxSize(),
                )
            }
        }

        if (
            uiState.isLoading ||
            uiState.isPreparingShooting
        ) {
            Box(
                modifier =
                    Modifier.fillMaxSize(),
                contentAlignment =
                    Alignment.Center,
            ) {
                LoadingWheel()
            }
        }

        CaptureBackButton(
            enabled =
                !uiState.isSubmitting,
            onClick =
                onBackClick,
            modifier = Modifier
                .align(
                    Alignment.TopStart,
                )
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
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource =
        remember {
            MutableInteractionSource()
        }

    Box(
        modifier = modifier
            .size(
                48.dp,
            )
            .alpha(
                if (enabled) {
                    1f
                } else {
                    DisabledAlpha
                },
            )
            .clickable(
                enabled =
                    enabled,
                interactionSource =
                    interactionSource,
                indication =
                    null,
                onClick =
                    onClick,
            ),
        contentAlignment =
            Alignment.Center,
    ) {
        Image(
            painter =
                painterResource(
                    DesignSystemR.drawable.ic_arrow_left_24,
                ),
            contentDescription =
                stringResource(
                    R.string.capture_back,
                ),
            colorFilter =
                ColorFilter.tint(
                    Color.White,
                ),
            modifier =
                Modifier.size(
                    32.dp,
                ),
        )
    }
}

private const val DisabledAlpha =
    0.45f