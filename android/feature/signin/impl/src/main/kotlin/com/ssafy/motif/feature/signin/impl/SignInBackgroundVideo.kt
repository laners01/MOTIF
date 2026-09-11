package com.ssafy.motif.feature.signin.impl

import android.graphics.Matrix
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

@OptIn(UnstableApi::class)
@Composable
fun SignInBackgroundVideo(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var videoSize by remember {
        mutableStateOf<VideoSize?>(null)
    }

    var containerSize by remember {
        mutableStateOf(IntSize.Zero)
    }

    val player = remember(context.applicationContext) {
        ExoPlayer.Builder(
            context.applicationContext,
        ).build().apply {
            val videoUri =
                "android.resource://${context.packageName}/${R.raw.signin_background}".toUri()

            setMediaItem(
                MediaItem.fromUri(videoUri),
            )

            repeatMode = Player.REPEAT_MODE_ONE

            // 배경 영상이므로 음소거
            volume = 0f

            playWhenReady = true

            prepare()
        }
    }

    DisposableEffect(
        player,
        lifecycleOwner,
    ) {
        val playerListener = object : Player.Listener {
            override fun onVideoSizeChanged(size: VideoSize) {
                videoSize = size
            }
        }

        val lifecycleObserver =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> {
                        player.play()
                    }

                    Lifecycle.Event.ON_STOP -> {
                        player.pause()
                    }

                    else -> Unit
                }
            }

        player.addListener(playerListener)

        lifecycleOwner.lifecycle.addObserver(
            lifecycleObserver,
        )

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(
                lifecycleObserver,
            )

            player.removeListener(
                playerListener,
            )

            player.release()
        }
    }

    AndroidView(
        modifier = modifier.onSizeChanged {
            containerSize = it
        },
        factory = { context ->
            TextureView(context).apply {
                player.setVideoTextureView(this)
            }
        },
        update = { textureView ->
            val currentVideoSize = videoSize
                ?: return@AndroidView

            textureView.updateCenterCropTransform(
                containerSize = containerSize,
                videoSize = currentVideoSize,
            )
        },
    )
}

private fun TextureView.updateCenterCropTransform(
    containerSize: IntSize,
    videoSize: VideoSize,
) {
    if (
        containerSize.width <= 0 ||
        containerSize.height <= 0 ||
        videoSize.width <= 0 ||
        videoSize.height <= 0
    ) {
        return
    }

    val viewWidth =
        containerSize.width.toFloat()

    val viewHeight =
        containerSize.height.toFloat()

    val videoWidth =
        videoSize.width *
                videoSize.pixelWidthHeightRatio

    val videoHeight =
        videoSize.height.toFloat()

    val viewAspectRatio =
        viewWidth / viewHeight

    val videoAspectRatio =
        videoWidth / videoHeight

    val scaleX: Float
    val scaleY: Float

    if (videoAspectRatio > viewAspectRatio) {
        /*
         * 영상이 화면보다 가로로 긴 경우.
         *
         * 세로를 맞추고 좌우를 Crop.
         */
        scaleX =
            videoAspectRatio / viewAspectRatio

        scaleY = 1f
    } else {
        /*
         * 영상이 화면보다 세로로 긴 경우.
         *
         * 가로를 맞추고 위아래를 Crop.
         */
        scaleX = 1f

        scaleY =
            viewAspectRatio / videoAspectRatio
    }

    val matrix = Matrix().apply {
        setScale(
            scaleX,
            scaleY,
            viewWidth / 2f,
            viewHeight / 2f,
        )
    }

    setTransform(matrix)
}