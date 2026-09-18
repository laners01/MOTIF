package com.ssafy.motif.feature.challenges.impl.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import com.ssafy.motif.core.designsystem.component.bottomsheet.SheetState
import com.ssafy.motif.core.designsystem.glass.GlassBackdropState
import com.ssafy.motif.core.ui.video.VideoShortsFeed
import com.ssafy.motif.core.ui.video.rememberVideoShortsState

@Composable
internal fun ChallengeShortFormLayout(
    itemCount: Int,
    videoId: (Int) -> String,
    videoUrl: (Int) -> String,
    thumbnailUrl: (Int) -> String,
    myProfileImageUrl: String,
    activeSheetState: SheetState?,
    onHomeClick: () -> Unit,
    onMyClick: () -> Unit,
    onMotifClick: () -> Unit,
    modifier: Modifier = Modifier,
    overlay: @Composable (
        index: Int,
        backdropState: GlassBackdropState,
    ) -> Unit,
) {
    val videoShortsState = rememberVideoShortsState()
    val isBottomSheetVisible = activeSheetState != null

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            VideoShortsFeed(
                itemCount = itemCount,
                videoId = videoId,
                videoUrl = videoUrl,
                thumbnailUrl = thumbnailUrl,
                state = videoShortsState,
                muted = false,
                repeat = true,
                captureBackdrop = true,
                userScrollEnabled = !isBottomSheetVisible,
                modifier = Modifier
                    .fillMaxSize()
                    .scaleForBottomSheet(
                        sheetState = activeSheetState,
                    ),
            ) { index, _, backdropState ->
                if (!isBottomSheetVisible) {
                    overlay(
                        index,
                        backdropState,
                    )
                }
            }
        }

        ChallengeBottomNavigation(
            playbackProgress = videoShortsState.playbackProgress,
            myProfileImageUrl = myProfileImageUrl,
            onSeek = videoShortsState::seekTo,
            onHomeClick = onHomeClick,
            onMyClick = onMyClick,
            onMotifClick = onMotifClick,
        )
    }
}

private fun Modifier.scaleForBottomSheet(
    sheetState: SheetState?,
): Modifier =
    graphicsLayer {
        val sheetTop = sheetState?.offsetOrNull()

        val scale = if (sheetTop == null || size.height <= 0f) {
            1f
        } else {
            (sheetTop / size.height).coerceIn(
                minimumValue = 0f,
                maximumValue = 1f,
            )
        }

        scaleX = scale
        scaleY = scale

        transformOrigin = TransformOrigin(
            pivotFractionX = 0.5f,
            pivotFractionY = 0f,
        )

        alpha = if (scale == 0f) {
            0f
        } else {
            1f
        }
    }