package com.ssafy.motif.feature.capture.impl.motif

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.source.MediaSource
import com.ssafy.motif.core.designsystem.component.LoadingWheel
import com.ssafy.motif.feature.capture.impl.R
import com.ssafy.motif.feature.capture.impl.motif.model.MotifCaptureProcessingState
import com.ssafy.motif.feature.capture.impl.motif.model.MotifCaptureStage
import com.ssafy.motif.feature.capture.impl.motif.model.MotifCaptureUiEffect
import com.ssafy.motif.feature.capture.impl.motif.model.MotifCaptureUiState
import com.ssafy.motif.feature.capture.impl.permission.CameraPermissionGate
import com.ssafy.motif.feature.capture.impl.permission.rememberCameraPermissionState
import com.ssafy.motif.feature.capture.impl.segmentation.rememberPersonSegmentationProcessor
import com.ssafy.motif.core.designsystem.R as DesignSystemR

@Composable
internal fun MotifCaptureScreen(
    assetId: Long,
    onBackClick: () -> Unit,
    onBrowseMotifClick: (
        artistId: Long,
        assetId: Long,
    ) -> Unit,
    onError: (Throwable) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MotifCaptureViewModel =
        hiltViewModel(),
) {
    LockLandscapeOrientation()

    val uiState by
    viewModel.uiState
        .collectAsStateWithLifecycle()

    val cameraPermissionState =
        rememberCameraPermissionState()

    LaunchedEffect(
        assetId,
    ) {
        viewModel.initialize(
            assetId =
                assetId,
        )
    }

    LaunchedEffect(
        viewModel,
    ) {
        viewModel.uiEffect
            .collect { effect ->
                when (effect) {
                    is MotifCaptureUiEffect.Error -> {
                        onError(
                            effect.throwable,
                        )
                    }
                }
            }
    }

    CameraPermissionGate(
        permissionState =
            cameraPermissionState,
        onBackClick =
            onBackClick,
        modifier =
            modifier,
    ) {
        MotifCaptureScreen(
            uiState =
                uiState,
            onBackClick =
                onBackClick,
            onStartClick =
                viewModel::startCapture,
            onShootingReady =
                viewModel::onShootingReady,
            onRecordingProgress =
                viewModel::onRecordingProgress,
            onRecordingFinished =
                viewModel::onRecordingFinished,
            onRecordingRetryReady =
                viewModel::onRecordingRetryReady,
            onReviewRetryClick =
                viewModel::retryRecording,
            onMakeMotifClick =
                viewModel::makeMotif,
            onRetryTransferClick =
                viewModel::retryTransfer,
            onBrowseMotifClick =
                onBrowseMotifClick,
            onError =
                viewModel::onError,
            modifier =
                Modifier.fillMaxSize(),
        )
    }
}

@Composable
internal fun MotifCaptureScreen(
    uiState: MotifCaptureUiState,
    onBackClick: () -> Unit,
    onStartClick: () -> Unit,
    onShootingReady: () -> Unit,
    onRecordingProgress: (Long) -> Unit,
    onRecordingFinished: (Uri) -> Unit,
    onRecordingRetryReady: () -> Unit,
    onReviewRetryClick: () -> Unit,
    onMakeMotifClick: () -> Unit,
    onRetryTransferClick: () -> Unit,
    onBrowseMotifClick: (
        artistId: Long,
        assetId: Long,
    ) -> Unit,
    onError: (Throwable) -> Unit,
    modifier: Modifier = Modifier,
    mediaSourceFactory: MediaSource.Factory? = null,
) {
    val isCaptureInProgress =
        uiState.stage ==
                MotifCaptureStage.Countdown ||
                uiState.stage ==
                MotifCaptureStage.Recording

    BackHandler(
        enabled =
            isCaptureInProgress ||
                    uiState.isTransferInProgress,
    ) {
        // 촬영 중 또는 서버 인계 중에는 화면 이탈을 막는다.
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(
                    Color.Black,
                ),
    ) {
        val asset =
            uiState.asset

        when (
            uiState.stage
        ) {
            MotifCaptureStage.Ready,
            MotifCaptureStage.Countdown,
            MotifCaptureStage.Recording,
            MotifCaptureStage.Review,
                -> {
                if (
                    asset != null
                ) {
                    val segmentationProcessor =
                        rememberPersonSegmentationProcessor()

                    MotifCaptureShootingScreen(
                        uiState =
                            uiState,
                        segmentationProcessor =
                            segmentationProcessor,
                        onStartClick =
                            onStartClick,
                        onShootingReady =
                            onShootingReady,
                        onRecordingProgress =
                            onRecordingProgress,
                        onRecordingFinished =
                            onRecordingFinished,
                        onRecordingRetryReady =
                            onRecordingRetryReady,
                        onReviewRetryClick =
                            onReviewRetryClick,
                        onMakeMotifClick =
                            onMakeMotifClick,
                        onError =
                            onError,
                        mediaSourceFactory =
                            mediaSourceFactory,
                        modifier =
                            Modifier.fillMaxSize(),
                    )
                }
            }

            MotifCaptureStage.Processing -> {
                MotifCaptureProcessingScreen(
                    previewImageUrl =
                        uiState.previewImageUrl,
                    processingState =
                        uiState.processingState,
                    uploadProgress =
                        uiState.uploadProgress,
                    recommendedAssets =
                        uiState.recommendedAssets,
                    onAssetClick =
                        onBrowseMotifClick,
                    onRetryClick =
                        onRetryTransferClick,
                    modifier =
                        Modifier.fillMaxSize(),
                )
            }
        }

        if (
            uiState.isLoading
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            Color.Black,
                        ),
                contentAlignment =
                    Alignment.Center,
            ) {
                LoadingWheel()
            }
        }

        val showBackButton =
            uiState.stage ==
                    MotifCaptureStage.Processing &&
                    (
                            uiState.processingState ==
                                    MotifCaptureProcessingState.Queued ||
                                    uiState.processingState ==
                                    MotifCaptureProcessingState.Failed
                            )

        if (
            showBackButton
        ) {
            MotifCaptureBackButton(
                onClick =
                    onBackClick,
                modifier =
                    Modifier
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
}

@Composable
private fun MotifCaptureBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource =
        remember {
            MutableInteractionSource()
        }

    Box(
        modifier =
            modifier
                .size(
                    48.dp,
                )
                .clickable(
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
                    DesignSystemR.drawable
                        .ic_arrow_left_24,
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

@Composable
private fun LockLandscapeOrientation() {
    val context =
        LocalContext.current

    val activity =
        remember(
            context,
        ) {
            context.findActivity()
        }

    DisposableEffect(
        activity,
    ) {
        if (
            activity == null
        ) {
            return@DisposableEffect onDispose { }
        }

        val previousOrientation =
            activity.requestedOrientation

        activity.requestedOrientation =
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        onDispose {
            /*
             * 가로 전환으로 Activity가 재생성되는 중에는 복원하지 않는다.
             * 실제 Navigation으로 MOTIF Capture를 벗어날 때만 기존 설정으로 되돌린다.
             */
            if (
                !activity.isChangingConfigurations
            ) {
                activity.requestedOrientation =
                    previousOrientation
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity ->
            this

        is ContextWrapper ->
            baseContext.findActivity()

        else ->
            null
    }