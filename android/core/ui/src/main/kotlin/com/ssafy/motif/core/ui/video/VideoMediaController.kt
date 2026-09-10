package com.ssafy.motif.core.ui.video

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.preload.DefaultPreloadManager

@OptIn(UnstableApi::class)
internal class VideoMediaController(
    val player: ExoPlayer,
    private val preloadManager: DefaultPreloadManager,
    private val preloadStatusControl: VideoPreloadStatusControl,
    private val mediaItems: List<MediaItem>,
) {

    fun play(
        index: Int,
    ) {
        val mediaItem =
            mediaItems.getOrNull(index)
                ?: return

        val mediaSource =
            preloadManager.getMediaSource(mediaItem)

        if (mediaSource != null) {
            player.setMediaSource(mediaSource)
        } else {
            player.setMediaItem(mediaItem)
        }

        preloadStatusControl.currentPlayingIndex =
            index

        preloadManager.setCurrentPlayingIndex(
            index,
        )

        player.prepare()
        player.play()
    }
}

@Composable
internal fun rememberVideoMediaItems(
    itemCount: Int,
    videoId: (index: Int) -> String,
    videoUrl: (index: Int) -> String,
): List<MediaItem> {
    val mediaSpecs =
        List(itemCount) { index ->
            VideoMediaSpec(
                id = videoId(index),
                url = videoUrl(index),
            )
        }

    return remember(mediaSpecs) {
        mediaSpecs.map { spec ->
            MediaItem.Builder()
                .setMediaId(spec.id)
                .setUri(spec.url)
                .build()
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
internal fun rememberVideoMediaController(
    mediaItems: List<MediaItem>,
    muted: Boolean,
    repeat: Boolean,
): VideoMediaController? {
    if (LocalInspectionMode.current) {
        return null
    }

    val context =
        LocalContext.current.applicationContext

    val cache = remember(context) {
        VideoMediaCache.getInstance(context)
    }

    val preloadStatusControl = remember {
        VideoPreloadStatusControl()
    }

    val preloadManagerBuilder = remember(
        context,
        cache,
        preloadStatusControl,
    ) {
        DefaultPreloadManager.Builder(
            context,
            preloadStatusControl,
        ).setCache(cache)
    }

    val preloadManager =
        remember(preloadManagerBuilder) {
            preloadManagerBuilder.build()
        }

    val player =
        remember(preloadManagerBuilder) {
            preloadManagerBuilder
                .buildExoPlayer()
        }

    SideEffect {
        player.volume =
            if (muted) {
                0f
            } else {
                1f
            }

        player.repeatMode =
            if (repeat) {
                Player.REPEAT_MODE_ONE
            } else {
                Player.REPEAT_MODE_OFF
            }
    }

    val controller = remember(
        player,
        preloadManager,
        preloadStatusControl,
        mediaItems,
    ) {
        VideoMediaController(
            player = player,
            preloadManager = preloadManager,
            preloadStatusControl = preloadStatusControl,
            mediaItems = mediaItems,
        )
    }

    DisposableEffect(
        preloadManager,
        mediaItems,
    ) {
        preloadManager.reset()

        preloadManager.addMediaItems(
            mediaItems,
            mediaItems.indices.toList(),
        )

        onDispose { }
    }

    DisposableEffect(
        player,
        preloadManager,
    ) {
        onDispose {
            player.release()
            preloadManager.release()
        }
    }

    return controller
}

private data class VideoMediaSpec(
    val id: String,
    val url: String,
)