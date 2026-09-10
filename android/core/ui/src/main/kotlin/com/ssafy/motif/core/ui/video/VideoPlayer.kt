package com.ssafy.motif.core.ui.video

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.ui.compose.PlayerSurface
import coil.compose.AsyncImage
import com.ssafy.motif.core.designsystem.component.LoadingWheel

@Composable
internal fun VideoPlayer(
    player: Player,
    thumbnailUrl: String,
    modifier: Modifier = Modifier,
) {
    var playbackState by remember(player) {
        mutableIntStateOf(
            player.playbackState,
        )
    }

    var hasRenderedFirstFrame by remember(player) {
        mutableStateOf(false)
    }

    DisposableEffect(player) {
        val listener =
            object : Player.Listener {

                override fun onMediaItemTransition(
                    mediaItem: MediaItem?,
                    reason: Int,
                ) {
                    if (
                        reason !=
                        Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
                    ) {
                        hasRenderedFirstFrame = false
                    }
                }

                override fun onPlaybackStateChanged(
                    state: Int,
                ) {
                    playbackState = state
                }

                override fun onRenderedFirstFrame() {
                    hasRenderedFirstFrame = true
                }
            }

        player.addListener(listener)

        onDispose {
            player.removeListener(listener)
        }
    }

    val showLoading =
        !hasRenderedFirstFrame ||
                playbackState == Player.STATE_BUFFERING

    Box(
        modifier = modifier,
    ) {
        PlayerSurface(
            player = player,
            modifier = Modifier.matchParentSize(),
        )

        if (!hasRenderedFirstFrame) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }

        if (showLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                LoadingWheel()
            }
        }
    }
}