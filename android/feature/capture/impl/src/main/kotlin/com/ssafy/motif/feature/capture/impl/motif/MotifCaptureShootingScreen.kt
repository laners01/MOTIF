package com.ssafy.motif.feature.capture.impl.motif

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.ui.compose.ContentFrame
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.feature.capture.impl.R
import com.ssafy.motif.feature.capture.impl.camera.CaptureCameraController
import com.ssafy.motif.feature.capture.impl.component.CaptureCountdownOverlay
import com.ssafy.motif.feature.capture.impl.motif.model.MotifCaptureStage
import com.ssafy.motif.feature.capture.impl.motif.model.MotifCaptureUiState
import com.ssafy.motif.feature.capture.impl.segmentation.PersonSegmentationProcessor
import java.io.File

@Composable
internal fun MotifCaptureShootingScreen(
    uiState: MotifCaptureUiState,
    segmentationProcessor: PersonSegmentationProcessor,
    onStartClick: () -> Unit,
    onShootingReady: () -> Unit,
    onRecordingProgress: (Long) -> Unit,
    onRecordingFinished: (Uri) -> Unit,
    onRecordingRetryReady: () -> Unit,
    onReviewRetryClick: () -> Unit,
    onMakeMotifClick: () -> Unit,
    onError: (Throwable) -> Unit,
    modifier: Modifier = Modifier,
    mediaSourceFactory: MediaSource.Factory? = null,
) {
    val asset =
        uiState.asset
            ?: return

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
            mutableStateOf(false)
        }

    var playerReady by
        remember {
            mutableStateOf(false)
        }

    var segmentationReady by
        remember {
            mutableStateOf(false)
        }

    var segmentedBitmap by
        remember {
            mutableStateOf<Bitmap?>(null)
        }

    var frozenReviewBitmap by
        remember {
            mutableStateOf<Bitmap?>(null)
        }

    var isRetryingRecording by
        remember {
            mutableStateOf(false)
        }

    val player =
        remember(
            context,
            asset.previewVideoUrl,
            mediaSourceFactory,
        ) {
            ExoPlayer
                .Builder(context)
                .apply {
                    mediaSourceFactory
                        ?.let {
                            setMediaSourceFactory(it)
                        }
                }
                .build()
                .apply {
                    setMediaItem(
                        MediaItem.fromUri(
                            asset.previewVideoUrl,
                        ),
                    )
                    playWhenReady = false
                    repeatMode = Player.REPEAT_MODE_OFF
                    prepare()
                }
        }

    val cameraController =
        remember(
            context,
            segmentationProcessor,
        ) {
            CaptureCameraController(
                context = context,
                segmentationProcessor =
                    segmentationProcessor,
            )
        }

    DisposableEffect(player) {
        val listener =
            object : Player.Listener {
                override fun onPlaybackStateChanged(
                    playbackState: Int,
                ) {
                    when (playbackState) {
                        Player.STATE_READY -> {
                            playerReady = true
                        }

                        Player.STATE_ENDED -> {
                            cameraController
                                .stopRecording()
                        }
                    }
                }
            }

        player.addListener(listener)

        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    DisposableEffect(
        cameraController,
        lifecycleOwner,
        targetRotation,
    ) {
        cameraController.bind(
            lifecycleOwner = lifecycleOwner,
            targetRotation = targetRotation,
            onReady = {
                cameraReady = true
            },
            onSegmentedFrame = { bitmap ->
                segmentedBitmap = bitmap
                segmentationReady = true
            },
            onError = onError,
        )

        onDispose {
            cameraController.release()
        }
    }

    LaunchedEffect(uiState.stage) {
        when (uiState.stage) {
            MotifCaptureStage.Ready -> {
                isRetryingRecording = false
                frozenReviewBitmap = null
                player.pause()
                player.seekTo(0L)
            }

            MotifCaptureStage.Countdown -> {
                player.pause()
                player.seekTo(0L)
            }

            MotifCaptureStage.Recording -> {
                cameraController
                    .startRecording(
                        onStarted = {
                            player.play()
                        },
                        onProgress =
                            onRecordingProgress,
                        onFinished = { uri ->
                            if (isRetryingRecording) {
                                deleteRecording(
                                    context = context,
                                    uri = uri,
                                )

                                isRetryingRecording = false
                                frozenReviewBitmap = null
                                player.seekTo(0L)

                                onRecordingRetryReady()
                            } else {
                                frozenReviewBitmap =
                                    segmentedBitmap

                                onRecordingFinished(uri)
                            }
                        },
                        onError = onError,
                    )
            }

            MotifCaptureStage.Review -> {
                player.pause()
            }

            MotifCaptureStage.Processing ->
                Unit
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
            MotifCaptureStage.Countdown
        ) {
            onShootingReady()
        }
    }

    val shootingReady =
        cameraReady &&
            playerReady &&
            segmentationReady

    val personBitmap =
        if (
            uiState.stage ==
            MotifCaptureStage.Review
        ) {
            frozenReviewBitmap
                ?: segmentedBitmap
        } else {
            segmentedBitmap
        }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black),
    ) {
        ContentFrame(
            player = player,
            contentScale = ContentScale.Crop,
            keepContentOnReset = true,
            modifier = Modifier.fillMaxSize(),
        )

        personBitmap
            ?.let { bitmap ->
                Image(
                    bitmap =
                        bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                // 현재 CaptureCameraController와 동일한 방향을 유지한다.
                                scaleX = 1f
                            },
                )
            }

        when (uiState.stage) {
            MotifCaptureStage.Ready -> {
                MotifCapturePlayButton(
                    enabled = shootingReady,
                    onClick = onStartClick,
                    modifier =
                        Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 40.dp),
                )
            }

            MotifCaptureStage.Countdown -> {
                CaptureCountdownOverlay(
                    countdown = uiState.countdown,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            MotifCaptureStage.Recording -> {
                MotifCaptureRetryButton(
                    onClick = {
                        if (isRetryingRecording) {
                            return@MotifCaptureRetryButton
                        }

                        isRetryingRecording = true
                        player.pause()
                        cameraController.stopRecording()
                    },
                    modifier =
                        Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 40.dp),
                )
            }

            MotifCaptureStage.Review -> {
                MotifCaptureReviewOverlay(
                    onRetryClick = onReviewRetryClick,
                    onMakeMotifClick =
                        onMakeMotifClick,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            MotifCaptureStage.Processing ->
                Unit
        }
    }
}

@Composable
private fun MotifCapturePlayButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource =
        remember {
            MutableInteractionSource()
        }

    val contentDescription =
        stringResource(
            R.string.motif_capture_start,
        )

    Box(
        modifier =
            modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(
                    Color.Black.copy(
                        alpha = 0.72f,
                    ),
                )
                .clickable(
                    enabled = enabled,
                    interactionSource =
                        interactionSource,
                    indication = null,
                    onClick = onClick,
                )
                .semantics {
                    this.contentDescription =
                        contentDescription
                },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier.size(34.dp),
        ) {
            val path =
                Path().apply {
                    moveTo(
                        size.width * 0.22f,
                        0f,
                    )
                    lineTo(
                        size.width * 0.22f,
                        size.height,
                    )
                    lineTo(
                        size.width,
                        size.height / 2f,
                    )
                    close()
                }

            drawPath(
                path = path,
                color =
                    Color.White.copy(
                        alpha =
                            if (enabled) {
                                1f
                            } else {
                                0.45f
                            },
                    ),
            )
        }
    }
}

@Composable
private fun MotifCaptureRetryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource =
        remember {
            MutableInteractionSource()
        }

    val contentDescription =
        stringResource(
            R.string.motif_capture_retry,
        )

    Box(
        modifier =
            modifier
                .size(72.dp)
                .clip(
                    RoundedCornerShape(16.dp),
                )
                .background(
                    Color.Black.copy(
                        alpha = 0.72f,
                    ),
                )
                .clickable(
                    interactionSource =
                        interactionSource,
                    indication = null,
                    onClick = onClick,
                )
                .semantics {
                    this.contentDescription =
                        contentDescription
                },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = "↻",
            style =
                TextStyle(
                    color = Color.White,
                    fontSize = 42.sp,
                    lineHeight = 42.sp,
                    fontWeight = FontWeight.Light,
                    textAlign = TextAlign.Center,
                ),
        )
    }
}

@Composable
private fun MotifCaptureReviewOverlay(
    onRetryClick: () -> Unit,
    onMakeMotifClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(
                    Color.Black.copy(
                        alpha = 0.58f,
                    ),
                ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment =
                Alignment.CenterHorizontally,
        ) {
            BasicText(
                text =
                    stringResource(
                        R.string
                            .motif_capture_review_title,
                    ),
                style =
                    TextStyle(
                        color = Color.White,
                        fontSize = 24.sp,
                        lineHeight = 32.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    ),
            )

            Spacer(
                modifier = Modifier.height(22.dp),
            )

            BasicText(
                text =
                    stringResource(
                        R.string
                            .motif_capture_review_question,
                    ),
                style = ReviewDescriptionStyle,
            )

            Spacer(
                modifier = Modifier.height(20.dp),
            )

            BasicText(
                text =
                    stringResource(
                        R.string
                            .motif_capture_review_description,
                    ),
                style = ReviewDescriptionStyle,
            )

            Spacer(
                modifier = Modifier.height(44.dp),
            )

            Row(
                horizontalArrangement =
                    Arrangement.Center,
                verticalAlignment =
                    Alignment.CenterVertically,
            ) {
                MotifCaptureRetryButton(
                    onClick = onRetryClick,
                )

                Spacer(
                    modifier = Modifier.width(28.dp),
                )

                MotifMakeButton(
                    onClick = onMakeMotifClick,
                )
            }
        }
    }
}

@Composable
private fun MotifMakeButton(
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
                .height(56.dp)
                .clip(
                    RoundedCornerShape(16.dp),
                )
                .background(MotifTheme.colors.pink)
                .clickable(
                    interactionSource =
                        interactionSource,
                    indication = null,
                    onClick = onClick,
                )
                .padding(
                    horizontal = 28.dp,
                ),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text =
                stringResource(
                    R.string.motif_capture_make,
                ),
            style =
                TextStyle(
                    color = Color.White,
                    fontSize = 20.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Medium,
                ),
        )
    }
}

private fun deleteRecording(
    context: Context,
    uri: Uri,
) {
    runCatching {
        when (uri.scheme) {
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
                    ?.let(::File)
                    ?.delete()
            }
        }
    }
}

private val ReviewDescriptionStyle =
    TextStyle(
        color = Color.White,
        fontSize = 20.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.Normal,
        textAlign = TextAlign.Center,
    )

