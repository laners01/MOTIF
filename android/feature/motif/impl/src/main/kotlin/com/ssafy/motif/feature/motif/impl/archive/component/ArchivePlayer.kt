package com.ssafy.motif.feature.motif.impl.archive.component

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
import androidx.compose.ui.tooling.preview.Preview
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.core.model.motif.SavedMotif
import com.ssafy.motif.core.ui.video.ControllableVideoPlayer
import com.ssafy.motif.core.ui.video.FullscreenVideoDialog
import com.ssafy.motif.core.ui.video.rememberControllableVideoPlayerState
import com.ssafy.motif.feature.motif.impl.archive.ArchiveMockData

/**
 * 보관함 상단의 재생 영역. 선택된 [motif]를 소리 있게 자동 재생하고, 전체화면 진입을 처리한다.
 *
 * [motif]가 바뀌면 같은 플레이어로 새 영상을 처음부터 재생한다.
 */
@Composable
internal fun ArchivePlayer(
    motif: SavedMotif,
    modifier: Modifier = Modifier,
) {
    val playerState = rememberControllableVideoPlayerState(
        videoId = motif.id.toString(),
        videoUrl = motif.videoUrl,
        thumbnailUrl = motif.thumbnailUrl,
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

@Preview(showBackground = true)
@Composable
private fun ArchivePlayerPreview() {
    MotifTheme {
        ArchivePlayer(
            motif = ArchiveMockData.savedMotifs.first(),
        )
    }
}
