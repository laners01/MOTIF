package com.ssafy.motif.core.ui.video

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.ssafy.motif.core.designsystem.component.Icon
import com.ssafy.motif.core.designsystem.glass.GlassBackdropState
import com.ssafy.motif.core.designsystem.icon.MotifIcons
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun VideoShortsFeed(
    itemCount: Int,
    videoId: (index: Int) -> String,
    videoUrl: (index: Int) -> String,
    thumbnailUrl: (index: Int) -> String,
    modifier: Modifier = Modifier,
    initialPage: Int = 0,
    state: VideoShortsState =
        rememberVideoShortsState(),
    muted: Boolean = false,
    repeat: Boolean = true,
    captureBackdrop: Boolean = false,
    userScrollEnabled: Boolean = true,
    onPlaybackStarted: (videoId: String) -> Unit = {},
    decoration: @Composable BoxScope.(
        index: Int,
        selected: Boolean,
        backdropState: GlassBackdropState,
    ) -> Unit = { _, _, _ -> },
) {
    if (itemCount <= 0) {
        return
    }

    val safeInitialPage =
        initialPage.coerceIn(
            minimumValue = 0,
            maximumValue =
                itemCount - 1,
        )

    val pagerState =
        rememberPagerState(
            initialPage =
                safeInitialPage,
            pageCount = {
                itemCount
            },
        )

    val mediaItems =
        rememberVideoMediaItems(
            itemCount = itemCount,
            videoId = videoId,
            videoUrl = videoUrl,
        )

    val mediaController =
        rememberVideoMediaController(
            mediaItems = mediaItems,
            muted = muted,
            repeat = repeat,
        )

    val currentOnPlaybackStarted by
    rememberUpdatedState(
        onPlaybackStarted,
    )

    var pendingPlaybackMediaId by
    remember {
        mutableStateOf<String?>(
            null,
        )
    }

    var lastPlaybackRequestedMediaId by
    remember {
        mutableStateOf<String?>(
            null,
        )
    }

    DisposableEffect(
        state,
        mediaController,
    ) {
        state.mediaController =
            mediaController

        onDispose {
            if (
                state.mediaController ===
                mediaController
            ) {
                state.mediaController =
                    null
            }
        }
    }

    DisposableEffect(
        mediaController,
    ) {
        val player =
            mediaController?.player

        if (player == null) {
            return@DisposableEffect onDispose {}
        }

        val listener =
            object : Player.Listener {

                override fun onIsPlayingChanged(
                    isPlaying: Boolean,
                ) {
                    
                    if (!isPlaying) {
                        return
                    }

                    val currentMediaId =
                        player
                            .currentMediaItem
                            ?.mediaId
                            ?: return

                    if (
                        pendingPlaybackMediaId !=
                        currentMediaId
                    ) {
                        return
                    }

                    pendingPlaybackMediaId =
                        null

                    currentOnPlaybackStarted(
                        currentMediaId,
                    )
                }
            }

        player.addListener(
            listener,
        )

        onDispose {
            player.removeListener(
                listener,
            )
        }
    }

    LaunchedEffect(
        pagerState.settledPage,
        mediaController,
    ) {
        val controller =
            mediaController
                ?: return@LaunchedEffect

        val page =
            pagerState.settledPage

        val targetMediaId =
            videoId(page)

        state.clearPlaybackFeedback()

        state.playbackProgress =
            0f

        /*
         * pagination 등으로 mediaController만 재생성된 경우
         * 같은 영상을 조회수로 다시 기록하지 않는다.
         *
         * A -> B -> A처럼 실제 페이지가 다시 변경된 경우에는
         * targetMediaId가 변경되므로 새로운 재생으로 기록된다.
         */
        if (
            lastPlaybackRequestedMediaId !=
            targetMediaId
        ) {
            pendingPlaybackMediaId =
                targetMediaId
        }

        lastPlaybackRequestedMediaId =
            targetMediaId

        controller.play(
            index = page,
        )
    }

    LaunchedEffect(
        mediaController,
    ) {
        if (mediaController == null) {
            return@LaunchedEffect
        }

        while (isActive) {
            state.updatePlaybackState()

            delay(
                PLAYBACK_PROGRESS_INTERVAL
                    .milliseconds,
            )
        }
    }

    VerticalPager(
        state = pagerState,
        userScrollEnabled =
            userScrollEnabled,
        modifier =
            modifier.fillMaxSize(),
    ) { page ->
        val selected =
            pagerState.settledPage ==
                    page

        VideoShortsItem(
            thumbnailUrl =
                thumbnailUrl(page),
            selected =
                selected,
            player =
                mediaController?.player,
            captureBackdrop =
                captureBackdrop,
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    enabled =
                        selected,
                    interactionSource =
                        remember {
                            MutableInteractionSource()
                        },
                    indication =
                        null,
                    onClick = {
                        state.togglePlayback()
                    },
                ),
            decoration = {
                    backdropState,
                ->

                decoration(
                    page,
                    selected,
                    backdropState,
                )

                if (selected) {
                    PlaybackFeedbackOverlay(
                        feedback =
                            state.playbackFeedback,
                        onFinished = {
                                feedback,
                            ->
                            state.clearPlaybackFeedback(
                                feedback,
                            )
                        },
                    )
                }
            },
        )
    }
}

@Composable
private fun BoxScope.PlaybackFeedbackOverlay(
    feedback: PlaybackFeedback?,
    onFinished: (PlaybackFeedback) -> Unit,
) {
    var displayedFeedback by remember {
        mutableStateOf<PlaybackFeedback?>(
            null,
        )
    }

    val alpha =
        remember {
            Animatable(
                initialValue = 0f,
            )
        }

    LaunchedEffect(
        feedback,
    ) {
        val currentFeedback =
            feedback
                ?: return@LaunchedEffect

        /*
         * 연속으로 빠르게 탭하면
         * 이전 animation effect가 취소되고
         * 새 아이콘을 바로 보여준다.
         */
        displayedFeedback =
            currentFeedback

        alpha.snapTo(
            1f,
        )

        delay(PLAYBACK_FEEDBACK_VISIBLE_DURATION.milliseconds)

        alpha.animateTo(
            targetValue = 0f,
            animationSpec =
                tween(
                    durationMillis =
                        PLAYBACK_FEEDBACK_FADE_OUT_DURATION,
                ),
        )

        displayedFeedback =
            null

        onFinished(
            currentFeedback,
        )
    }

    val currentFeedback =
        displayedFeedback
            ?: return

    Box(
        modifier = Modifier
            .align(
                Alignment.Center,
            )
            .size(
                PLAYBACK_FEEDBACK_SIZE,
            )
            .graphicsLayer {
                this.alpha =
                    alpha.value
            }
            .clip(
                CircleShape,
            )
            .background(
                Color.Black.copy(
                    alpha =
                        PLAYBACK_FEEDBACK_BACKGROUND_ALPHA,
                ),
            ),
        contentAlignment =
            Alignment.Center,
    ) {
        when (currentFeedback) {
            PlaybackFeedback.Play -> {
                Icon(
                    painter = painterResource(
                        MotifIcons.FilledPlay,
                    ),
                    contentDescription =
                        "재생",
                    tint =
                        Color.White,
                    modifier =
                        Modifier.size(
                            PLAYBACK_FEEDBACK_ICON_SIZE,
                        ),
                )
            }

            PlaybackFeedback.Pause -> {
                PauseIcon()
            }
        }
    }
}

@Composable
internal fun PauseIcon(
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement =
            Arrangement.spacedBy(
                PAUSE_ICON_BAR_GAP,
            ),
        verticalAlignment =
            Alignment.CenterVertically,
    ) {
        PauseIconBar()

        PauseIconBar()
    }
}

@Composable
private fun PauseIconBar() {
    Box(
        modifier = Modifier
            .width(
                PAUSE_ICON_BAR_WIDTH,
            )
            .height(
                PAUSE_ICON_BAR_HEIGHT,
            )
            .clip(
                RoundedCornerShape(
                    PAUSE_ICON_BAR_RADIUS,
                ),
            )
            .background(
                Color.White,
            ),
    )
}

private const val PLAYBACK_PROGRESS_INTERVAL =
    100L

private const val PLAYBACK_FEEDBACK_VISIBLE_DURATION =
    500L

private const val PLAYBACK_FEEDBACK_FADE_OUT_DURATION =
    200

private const val PLAYBACK_FEEDBACK_BACKGROUND_ALPHA =
    0.55f

private val PLAYBACK_FEEDBACK_SIZE =
    64.dp

private val PLAYBACK_FEEDBACK_ICON_SIZE =
    30.dp

private val PAUSE_ICON_BAR_WIDTH =
    5.dp

private val PAUSE_ICON_BAR_HEIGHT =
    26.dp

private val PAUSE_ICON_BAR_GAP =
    7.dp

private val PAUSE_ICON_BAR_RADIUS =
    1.dp

@Preview(
    showBackground = true,
)
@Composable
private fun VideoShortsFeedPreview() {
    MotifTheme {
        VideoShortsFeed(
            itemCount =
                previewVideoUrls.size,
            videoId = { index ->
                index.toString()
            },
            videoUrl = { index ->
                previewVideoUrls[index]
            },
            thumbnailUrl = { index ->
                previewThumbnailUrls[index]
            },
            modifier =
                Modifier.fillMaxSize(),
            muted = true,
            repeat = true,
            captureBackdrop = false,
        )
    }
}

private val previewVideoUrls =
    listOf(
        "https://d21so2cpi8si9r.cloudfront.net/challenges/d7f49b37-54e7-4e2f-888e-3d24f6f453d4/master.m3u8",
        "https://d21so2cpi8si9r.cloudfront.net/challenges/5ece3915-c32b-4836-8b55-868e49a8d8f0/master.m3u8",
        "https://res.cloudinary.com/demo/video/upload/w_540,h_960,c_fill/pencil_sketch.mp4",
        "https://res.cloudinary.com/demo/video/upload/w_540,h_960,c_fill/turtle.mp4",
        "https://res.cloudinary.com/demo/video/upload/w_540,h_960,c_fill/elephants.mp4",
    )

private val previewThumbnailUrls =
    listOf(
        "https://res.cloudinary.com/demo/video/upload/so_1,w_540,h_960,c_fill/kitten_fighting.jpg",
        "https://res.cloudinary.com/demo/video/upload/so_1,w_540,h_960,c_fill/dog.jpg",
        "https://res.cloudinary.com/demo/video/upload/so_1,w_540,h_960,c_fill/pencil_sketch.jpg",
        "https://res.cloudinary.com/demo/video/upload/so_1,w_540,h_960,c_fill/turtle.jpg",
        "https://res.cloudinary.com/demo/video/upload/so_1,w_540,h_960,c_fill/elephants.jpg",
    )