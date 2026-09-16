package com.ssafy.motif.core.ui.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

internal enum class PlaybackFeedback {
    Play,
    Pause,
}

@Stable
class VideoShortsState internal constructor() {

    internal var mediaController:
            VideoMediaController? = null

    var playbackProgress by
    mutableFloatStateOf(0f)
        internal set

    var isPlaying by
    mutableStateOf(false)
        internal set

    internal var playbackFeedback by
    mutableStateOf<PlaybackFeedback?>(
        null,
    )
        private set

    fun togglePlayback() {
        val controller =
            mediaController
                ?: return

        val isNowPlaying =
            controller.togglePlayback()

        isPlaying =
            isNowPlaying

        playbackFeedback =
            if (isNowPlaying) {
                PlaybackFeedback.Play
            } else {
                PlaybackFeedback.Pause
            }
    }

    fun seekTo(
        progress: Float,
    ) {
        val targetProgress =
            progress.coerceIn(
                0f,
                1f,
            )

        mediaController
            ?.seekTo(
                targetProgress,
            )

        playbackProgress =
            targetProgress
    }

    internal fun updatePlaybackState() {
        val controller =
            mediaController
                ?: return

        playbackProgress =
            controller.playbackProgress()

        isPlaying =
            controller.player.isPlaying
    }

    internal fun clearPlaybackFeedback(
        feedback: PlaybackFeedback,
    ) {
        if (
            playbackFeedback ==
            feedback
        ) {
            playbackFeedback =
                null
        }
    }

    internal fun clearPlaybackFeedback() {
        playbackFeedback =
            null
    }
}

@Composable
fun rememberVideoShortsState():
        VideoShortsState =
    remember {
        VideoShortsState()
    }