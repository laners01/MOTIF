package com.ssafy.motif.core.ui.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ssafy.motif.core.designsystem.component.Icon
import com.ssafy.motif.core.designsystem.component.IconButton
import com.ssafy.motif.core.designsystem.component.ModifIconButtonDefaults
import com.ssafy.motif.core.designsystem.icon.MotifIcons
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import kotlinx.coroutines.delay

/**
 * 탭하면 컨트롤(재생/일시정지, 음소거, 전체화면)이 나타나는 단일 영상 플레이어.
 *
 * 재생 중이면 컨트롤은 [CONTROLS_AUTO_HIDE_MILLIS] 후 자동으로 사라지고, 일시정지 상태에서는 계속 표시된다.
 * 인라인과 전체화면([FullscreenVideoDialog])에서 같은 [state]로 호출해 재생을 이어간다.
 *
 * @param isFullscreen 전체화면 안에서 그려지는지 여부. 전체화면 버튼의 의미(진입/종료)만 바뀐다.
 */
@Composable
fun ControllableVideoPlayer(
    state: ControllableVideoPlayerState,
    onFullscreenClick: () -> Unit,
    modifier: Modifier = Modifier,
    isFullscreen: Boolean = false,
) {
    var controlsVisible by remember { mutableStateOf(false) }

    LaunchedEffect(controlsVisible, state.isPlaying) {
        if (controlsVisible && state.isPlaying) {
            delay(CONTROLS_AUTO_HIDE_MILLIS)
            controlsVisible = false
        }
    }

    Box(
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = { controlsVisible = !controlsVisible },
        ),
    ) {
        val player = state.player

        if (player != null) {
            VideoPlayer(
                player = player,
                thumbnailUrl = state.thumbnailUrl,
                modifier = Modifier.matchParentSize(),
            )
        } else {
            // Preview 등 플레이어를 만들 수 없는 환경에서는 썸네일만 보여준다.
            AsyncImage(
                model = state.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.matchParentSize(),
        ) {
            VideoControlsOverlay(
                isPlaying = state.isPlaying,
                isMuted = state.isMuted,
                isFullscreen = isFullscreen,
                onPlayPauseClick = state::togglePlayback,
                onMuteClick = state::toggleMute,
                onFullscreenClick = onFullscreenClick,
            )
        }
    }
}

@Composable
private fun VideoControlsOverlay(
    isPlaying: Boolean,
    isMuted: Boolean,
    isFullscreen: Boolean,
    onPlayPauseClick: () -> Unit,
    onMuteClick: () -> Unit,
    onFullscreenClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = CONTROLS_SCRIM_ALPHA)),
    ) {
        PlayPauseButton(
            isPlaying = isPlaying,
            onClick = onPlayPauseClick,
            modifier = Modifier.align(Alignment.Center),
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(CONTROLS_EDGE_PADDING),
            horizontalArrangement = Arrangement.spacedBy(CONTROLS_BUTTON_GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onMuteClick,
                painter = painterResource(
                    if (isMuted) MotifIcons.Mute else MotifIcons.Volume,
                ),
                contentDescription = if (isMuted) "음소거 해제" else "음소거",
                size = CONTROLS_BUTTON_SIZE,
                padding = PaddingValues(CONTROLS_BUTTON_INNER_PADDING),
                colors = ModifIconButtonDefaults.iconButtonColors(
                    contentColor = Color.White,
                ),
            )

            IconButton(
                onClick = onFullscreenClick,
                painter = painterResource(MotifIcons.Maximise),
                contentDescription = if (isFullscreen) "전체화면 종료" else "전체화면",
                size = CONTROLS_BUTTON_SIZE,
                padding = PaddingValues(CONTROLS_BUTTON_INNER_PADDING),
                colors = ModifIconButtonDefaults.iconButtonColors(
                    contentColor = Color.White,
                ),
            )
        }
    }
}

@Composable
private fun PlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(PLAY_PAUSE_BUTTON_SIZE)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = PLAY_PAUSE_BACKGROUND_ALPHA))
            .clickable(
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (isPlaying) {
            PauseIcon()
        } else {
            Icon(
                painter = painterResource(MotifIcons.FilledPlay),
                contentDescription = "재생",
                tint = Color.White,
                modifier = Modifier.size(PLAY_ICON_SIZE),
            )
        }
    }
}

private const val CONTROLS_AUTO_HIDE_MILLIS = 3_000L
private const val CONTROLS_SCRIM_ALPHA = 0.35f
private const val PLAY_PAUSE_BACKGROUND_ALPHA = 0.55f

private val CONTROLS_EDGE_PADDING = 12.dp
private val CONTROLS_BUTTON_GAP = 4.dp
private val CONTROLS_BUTTON_SIZE = 40.dp
private val CONTROLS_BUTTON_INNER_PADDING = 8.dp
private val PLAY_PAUSE_BUTTON_SIZE = 56.dp
private val PLAY_ICON_SIZE = 28.dp

@Preview(showBackground = true)
@Composable
private fun ControllableVideoPlayerPreview() {
    MotifTheme {
        val state = rememberControllableVideoPlayerState(
            videoId = "preview",
            videoUrl = "",
            thumbnailUrl = "https://res.cloudinary.com/demo/video/upload/so_1,w_540,h_960,c_fill/dog.jpg",
        )

        ControllableVideoPlayer(
            state = state,
            onFullscreenClick = {},
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
        )
    }
}
