package com.ssafy.motif.feature.motif.impl.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.ssafy.motif.core.ui.video.ControllableVideoPlayer
import com.ssafy.motif.core.ui.video.FullscreenVideoDialog
import com.ssafy.motif.core.ui.video.rememberControllableVideoPlayerState

/**
 * 모티프 서브 화면 공용 16:9 영상 플레이어. 소리 있게 자동 재생하고 전체화면 진입을 처리한다.
 *
 * [videoId] / [videoUrl]이 바뀌면 같은 플레이어로 새 영상을 처음부터 재생한다.
 * 보관함 상단 재생 영역과 상품 상세 미리보기가 함께 쓴다.
 */
@Composable
internal fun MotifVideoPlayer(
    videoId: String,
    videoUrl: String,
    thumbnailUrl: String,
    modifier: Modifier = Modifier,
) {
    val playerState = rememberControllableVideoPlayerState(
        videoId = videoId,
        videoUrl = videoUrl,
        thumbnailUrl = thumbnailUrl,
        initialMuted = false,
    )

    var isFullscreen by rememberSaveable { mutableStateOf(false) }

    val playerModifier = modifier
        .fillMaxWidth()
        .aspectRatio(PlayerAspectRatio)

    if (isFullscreen) {
        // 플레이어 서피스는 한 곳에만 붙을 수 있으므로, 전체화면 중에는 인라인 자리를 비워 둔다.
        Box(
            modifier = playerModifier.background(Color.Black),
        )

        FullscreenVideoDialog(
            state = playerState,
            onDismiss = { isFullscreen = false },
            aspectRatio = PlayerAspectRatio,
        )
    } else {
        ControllableVideoPlayer(
            state = playerState,
            onFullscreenClick = { isFullscreen = true },
            modifier = playerModifier,
        )
    }
}

private const val PlayerAspectRatio = 16f / 9f
