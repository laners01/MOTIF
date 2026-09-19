package com.ssafy.motif.core.ui.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.media3.common.Player

/**
 * 단일 영상을 재생하는 플레이어의 상태.
 *
 * 인라인 플레이어와 전체화면 다이얼로그가 같은 인스턴스를 공유하도록
 * [rememberControllableVideoPlayerState]로 화면 단위에서 만들어 내려준다.
 * 그래야 전체화면을 오갈 때 재생 위치·음소거 상태가 끊기지 않는다.
 */
@Stable
class ControllableVideoPlayerState internal constructor(
    internal val mediaController: VideoMediaController?,
    val thumbnailUrl: String,
    private val mutedState: MutableState<Boolean>,
) {
    val player: Player?
        get() = mediaController?.player

    var isMuted: Boolean
        get() = mutedState.value
        private set(value) {
            mutedState.value = value
        }

    var isPlaying: Boolean by mutableStateOf(false)
        internal set

    fun togglePlayback() {
        mediaController?.togglePlayback()
    }

    fun toggleMute() {
        isMuted = !isMuted
    }
}

/**
 * [videoId] / [videoUrl]이 바뀌면 같은 플레이어로 새 영상을 처음부터 재생한다.
 *
 * @param initialMuted 첫 진입 시 음소거 여부. 이후 변경은 [ControllableVideoPlayerState.toggleMute]로.
 */
@Composable
fun rememberControllableVideoPlayerState(
    videoId: String,
    videoUrl: String,
    thumbnailUrl: String,
    initialMuted: Boolean = false,
    repeat: Boolean = true,
): ControllableVideoPlayerState {
    val mutedState = rememberSaveable { mutableStateOf(initialMuted) }

    val mediaItems = rememberVideoMediaItems(
        itemCount = 1,
        videoId = { videoId },
        videoUrl = { videoUrl },
    )

    val mediaController = rememberVideoMediaController(
        mediaItems = mediaItems,
        muted = mutedState.value,
        repeat = repeat,
    )

    val state = remember(mediaController, thumbnailUrl, mutedState) {
        ControllableVideoPlayerState(
            mediaController = mediaController,
            thumbnailUrl = thumbnailUrl,
            mutedState = mutedState,
        )
    }

    // 영상이 바뀌면(mediaItems 변경 → controller 재생성) 처음부터 재생한다.
    LaunchedEffect(mediaController) {
        mediaController?.play(index = 0)
    }

    val player = mediaController?.player
    DisposableEffect(player, state) {
        if (player == null) return@DisposableEffect onDispose {}

        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                state.isPlaying = isPlaying
            }
        }

        state.isPlaying = player.isPlaying
        player.addListener(listener)

        onDispose {
            player.removeListener(listener)
        }
    }

    return state
}
