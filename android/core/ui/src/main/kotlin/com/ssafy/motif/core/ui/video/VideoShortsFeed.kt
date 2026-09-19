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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
    state: VideoShortsState =
        rememberVideoShortsState(),
    muted: Boolean = false,
    repeat: Boolean = true,
    captureBackdrop: Boolean = false,
    userScrollEnabled: Boolean = true,
    decoration: @Composable BoxScope.(
        index: Int,
        selected: Boolean,
        backdropState: GlassBackdropState,
    ) -> Unit = { _, _, _ -> },
) {
    if (itemCount <= 0) {
        return
    }

    val pagerState =
        rememberPagerState(
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

    /*
     * 페이지 전환이 완료되면
     * 해당 영상 재생.
     *
     * 이전 영상에서 보여주던
     * Play/Pause 피드백도 제거한다.
     */
    LaunchedEffect(
        pagerState.settledPage,
        mediaController,
    ) {
        val controller =
            mediaController
                ?: return@LaunchedEffect

        state.clearPlaybackFeedback()

        state.playbackProgress =
            0f

        controller.play(
            index =
                pagerState.settledPage,
        )
    }

    /*
     * 영상 진행률과 실제 재생 상태 갱신.
     */
    LaunchedEffect(
        mediaController,
    ) {
        if (mediaController == null) {
            return@LaunchedEffect
        }

        while (isActive) {
            state.updatePlaybackState()

            delay(PLAYBACK_PROGRESS_INTERVAL.milliseconds)
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
                    indication = null,
                    onClick = {
                        state.togglePlayback()
                    },
                ),
            decoration = { backdropState ->

                /*
                 * 각 feature에서 전달한
                 * 기존 overlay.
                 */
                decoration(
                    page,
                    selected,
                    backdropState,
                )

                /*
                 * Challenge overlay 등과 별개로
                 * 영상 자체에서 제공하는
                 * Play / Pause 피드백.
                 *
                 * 따라서 Challenge BottomSheet가 열려
                 * ChallengeShortFormOverlay가 사라져도
                 * 이 아이콘은 계속 표시된다.
                 */
                if (selected) {
                    PlaybackFeedbackOverlay(
                        feedback =
                            state.playbackFeedback,
                        onFinished = { feedback ->
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
private fun PauseIcon(
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