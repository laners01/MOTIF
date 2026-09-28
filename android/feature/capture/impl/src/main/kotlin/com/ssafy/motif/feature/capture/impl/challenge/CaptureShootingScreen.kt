package com.ssafy.motif.feature.capture.impl.challenge

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.ui.compose.ContentFrame
import coil.compose.AsyncImage
import com.ssafy.motif.feature.capture.impl.camera.CaptureCameraController
import com.ssafy.motif.feature.capture.impl.challenge.model.CaptureStage
import com.ssafy.motif.feature.capture.impl.challenge.model.CaptureUiState
import com.ssafy.motif.feature.capture.impl.challenge.shooting.CaptureRecordingOverlay
import com.ssafy.motif.feature.capture.impl.component.CaptureCountdownOverlay
import com.ssafy.motif.feature.capture.impl.segmentation.PersonSegmentationProcessor
import java.io.File

@Composable
internal fun CaptureShootingScreen(
    uiState: CaptureUiState,
    segmentationProcessor: PersonSegmentationProcessor,
    onShootingReady: () -> Unit,
    onRecordingProgress: (Long) -> Unit,
    onRecordingFinished: (Uri) -> Unit,
    onRetryReady: () -> Unit,
    onError: (Throwable) -> Unit,
    modifier: Modifier = Modifier,
    mediaSourceFactory: MediaSource.Factory? = null,
) {
    val context =
        LocalContext.current

    val lifecycleOwner =
        LocalLifecycleOwner.current

    val view =
        LocalView.current

    val targetRotation =
        view.display.rotation

    var cameraReady by
    remember {
        mutableStateOf(
            false,
        )
    }

    var playerReady by
    remember {
        mutableStateOf(
            false,
        )
    }

    var segmentationReady by
    remember {
        mutableStateOf(
            false,
        )
    }

    var segmentedBitmap by
    remember {
        mutableStateOf<Bitmap?>(
            null,
        )
    }

    var isRetrying by
    remember {
        mutableStateOf(
            false,
        )
    }

    val player =
        remember(
            context,
            uiState.guideVideoUrl,
            mediaSourceFactory,
        ) {
            ExoPlayer
                .Builder(
                    context,
                )
                .apply {
                    mediaSourceFactory
                        ?.let {
                            setMediaSourceFactory(
                                it,
                            )
                        }
                }
                .build()
                .apply {
                    setMediaItem(
                        MediaItem.fromUri(
                            uiState.guideVideoUrl,
                        ),
                    )

                    playWhenReady =
                        false

                    repeatMode =
                        Player.REPEAT_MODE_OFF

                    prepare()
                }
        }

    val cameraController =
        remember(
            context,
            segmentationProcessor,
        ) {
            CaptureCameraController(
                context =
                    context,
                segmentationProcessor =
                    segmentationProcessor,
            )
        }

    DisposableEffect(
        player,
    ) {
        val listener =
            object : Player.Listener {

                override fun onPlaybackStateChanged(
                    playbackState: Int,
                ) {
                    when (
                        playbackState
                    ) {
                        Player.STATE_READY -> {
                            playerReady =
                                true
                        }

                        Player.STATE_ENDED -> {
                            cameraController
                                .stopRecording()
                        }
                    }
                }
            }

        player.addListener(
            listener,
        )

        onDispose {
            player.removeListener(
                listener,
            )

            player.release()
        }
    }

    DisposableEffect(
        cameraController,
        lifecycleOwner,
        targetRotation,
    ) {
        cameraController.bind(
            lifecycleOwner =
                lifecycleOwner,
            targetRotation =
                targetRotation,
            onReady = {
                cameraReady =
                    true
            },
            onSegmentedFrame = { bitmap ->
                segmentedBitmap =
                    bitmap

                segmentationReady =
                    true
            },
            onError =
                onError,
        )

        onDispose {
            cameraController.release()
        }
    }

    LaunchedEffect(
        uiState.stage,
    ) {
        when (
            uiState.stage
        ) {
            CaptureStage.Countdown -> {
                player.pause()

                player.seekTo(
                    0L,
                )
            }

            CaptureStage.Recording -> {
                cameraController
                    .startRecording(
                        onStarted = {
                            player.play()
                        },
                        onProgress =
                            onRecordingProgress,
                        onFinished = { uri ->
                            if (isRetrying) {
                                deleteRecording(
                                    context =
                                        context,
                                    uri =
                                        uri,
                                )

                                isRetrying =
                                    false

                                player.seekTo(
                                    0L,
                                )

                                onRetryReady()
                            } else {
                                onRecordingFinished(
                                    uri,
                                )
                            }
                        },
                        onError =
                            onError,
                    )
            }

            CaptureStage.PositionSelection,
            CaptureStage.Processing,
                -> Unit
        }
    }

    LaunchedEffect(
        cameraReady,
        playerReady,
        segmentationReady,
        uiState.stage,
    ) {
        if (
            cameraReady &&
            playerReady &&
            segmentationReady &&
            uiState.stage ==
            CaptureStage.Countdown
        ) {
            onShootingReady()
        }
    }

    val isShootingReady =
        cameraReady &&
                playerReady &&
                segmentationReady

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Color.Black,
            ),
    ) {
        ContentFrame(
            player =
                player,
            contentScale =
                ContentScale.Crop,
            keepContentOnReset =
                true,
            modifier =
                Modifier.fillMaxSize(),
        )

        segmentedBitmap
            ?.let { bitmap ->
                Image(
                    bitmap =
                        bitmap
                            .asImageBitmap(),
                    contentDescription =
                        null,
                    contentScale =
                        ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX =
                                1f
                        },
                )
            }

        if (
            !isShootingReady &&
            uiState.previewImageUrl.isNotBlank()
        ) {
            AsyncImage(
                model =
                    uiState.previewImageUrl,
                contentDescription =
                    null,
                contentScale =
                    ContentScale.Crop,
                modifier =
                    Modifier.fillMaxSize(),
            )
        }

        when (
            uiState.stage
        ) {
            CaptureStage.Countdown -> {
                CaptureCountdownOverlay(
                    countdown =
                        uiState.countdown,
                    modifier =
                        Modifier.fillMaxSize(),
                )
            }

            CaptureStage.Recording -> {
                CaptureRecordingOverlay(
                    elapsedTimeMs =
                        uiState.elapsedTimeMs,
                    durationMs =
                        uiState.durationMs,
                    retryEnabled =
                        !isRetrying,
                    onRetryClick = {
                        if (isRetrying) {
                            return@CaptureRecordingOverlay
                        }

                        isRetrying =
                            true

                        player.pause()

                        cameraController
                            .stopRecording()
                    },
                    modifier =
                        Modifier.fillMaxSize(),
                )
            }

            CaptureStage.PositionSelection,
            CaptureStage.Processing,
                -> Unit
        }
    }
}

private fun deleteRecording(
    context: Context,
    uri: Uri,
) {
    runCatching {
        when (
            uri.scheme
        ) {
            ContentResolver.SCHEME_CONTENT -> {
                context
                    .contentResolver
                    .delete(
                        uri,
                        null,
                        null,
                    )
            }

            ContentResolver.SCHEME_FILE -> {
                uri.path
                    ?.let(
                        ::File,
                    )
                    ?.delete()
            }
        }
    }
}