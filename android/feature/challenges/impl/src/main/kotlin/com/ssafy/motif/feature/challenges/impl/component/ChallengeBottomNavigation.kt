package com.ssafy.motif.feature.challenges.impl.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ssafy.motif.core.designsystem.component.Icon
import com.ssafy.motif.core.designsystem.component.Text
import com.ssafy.motif.core.designsystem.icon.MotifIcons
import com.ssafy.motif.core.designsystem.theme.MotifTheme

@Composable
internal fun ChallengeBottomNavigation(
    playbackProgress: Float,
    myProfileImageUrl: String,
    onSeek: (Float) -> Unit,
    onHomeClick: () -> Unit,
    onMyClick: () -> Unit,
    onMotifClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                MotifTheme.colors.background,
            )
            .windowInsetsPadding(
                WindowInsets.navigationBars,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChallengeNavigationItem(
                icon = MotifIcons.Home,
                label = "홈",
                selected = false,
                onClick = onHomeClick,
            )

            ChallengeNavigationItem(
                icon = MotifIcons.Play,
                label = "챌린지",
                selected = true,
                onClick = {},
            )

            ChallengeMyNavigationItem(
                profileImageUrl = myProfileImageUrl,
                label = "MY",
                onClick = onMyClick,
            )

            ChallengeMotifNavigationItem(
                onClick = onMotifClick,
            )
        }

        ChallengePlaybackProgress(
            progress = playbackProgress,
            onSeek = onSeek,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(
                    y = -(PLAYBACK_PROGRESS_TOUCH_HEIGHT / 2),
                ),
        )
    }
}

@Composable
private fun ChallengePlaybackProgress(
    progress: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clampedProgress =
        progress.coerceIn(
            minimumValue = 0f,
            maximumValue = 1f,
        )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(PLAYBACK_PROGRESS_TOUCH_HEIGHT)
            .pointerInput(onSeek) {
                detectTapGestures { offset ->
                    val targetProgress =
                        (offset.x / size.width.toFloat())
                            .coerceIn(
                                minimumValue = 0f,
                                maximumValue = 1f,
                            )

                    onSeek(targetProgress)
                }
            }
            .pointerInput(onSeek) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val targetProgress =
                            (offset.x / size.width.toFloat())
                                .coerceIn(
                                    minimumValue = 0f,
                                    maximumValue = 1f,
                                )

                        onSeek(targetProgress)
                    },
                    onDrag = { change, _ ->
                        change.consume()

                        val targetProgress =
                            (change.position.x / size.width.toFloat())
                                .coerceIn(
                                    minimumValue = 0f,
                                    maximumValue = 1f,
                                )

                        onSeek(targetProgress)
                    },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(PLAYBACK_PROGRESS_TRACK_HEIGHT)
                .background(
                    Color.White.copy(
                        alpha = 0.5f,
                    ),
                ),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth(
                    fraction = clampedProgress,
                )
                .height(PLAYBACK_PROGRESS_TRACK_HEIGHT)
                .background(
                    Color.White,
                ),
        )

        val availableWidth =
            maxWidth - PLAYBACK_PROGRESS_THUMB_SIZE

        Box(
            modifier = Modifier
                .offset(
                    x = availableWidth * clampedProgress,
                )
                .size(
                    PLAYBACK_PROGRESS_THUMB_SIZE,
                )
                .clip(
                    CircleShape,
                )
                .background(
                    Color.White,
                ),
        )
    }
}

@Composable
private fun RowScope.ChallengeNavigationItem(
    icon: Int,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val contentColor =
        if (selected) {
            Color.White
        } else {
            MotifTheme.colors.gray500
        }

    Column(
        modifier = Modifier
            .weight(1f)
            .clickable(
                interactionSource = remember {
                    MutableInteractionSource()
                },
                indication = null,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(
                icon,
            ),
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(24.dp),
        )

        Text(
            text = label,
            color = contentColor,
            style = MotifTheme.typography.bodySB12,
            modifier = Modifier.padding(
                top = 4.dp,
            ),
        )
    }
}

@Composable
private fun RowScope.ChallengeMyNavigationItem(
    profileImageUrl: String,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clickable(
                interactionSource = remember {
                    MutableInteractionSource()
                },
                indication = null,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AsyncImage(
            model = profileImageUrl,
            contentDescription = label,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(28.dp)
                .clip(
                    CircleShape,
                ),
        )

        Text(
            text = label,
            color = MotifTheme.colors.gray500,
            style = MotifTheme.typography.bodySB12,
            modifier = Modifier.padding(
                top = 4.dp,
            ),
        )
    }
}

@Composable
private fun RowScope.ChallengeMotifNavigationItem(
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clickable(
                interactionSource = remember {
                    MutableInteractionSource()
                },
                indication = null,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(
                MotifIcons.Motif,
            ),
            contentDescription = "모티프",
            tint = Color.Unspecified,
            modifier = Modifier
                .size(24.dp)
                .border(
                    width = 1.dp,
                    color = MotifTheme.colors.gray500,
                    shape = RoundedCornerShape(
                        4.dp,
                    ),
                ),
        )

        Text(
            text = "모티프",
            color = MotifTheme.colors.gray500,
            style = MotifTheme.typography.bodySB12,
            modifier = Modifier.padding(
                top = 4.dp,
            ),
        )
    }
}

private val PLAYBACK_PROGRESS_TOUCH_HEIGHT = 12.dp

private val PLAYBACK_PROGRESS_TRACK_HEIGHT = 2.dp

private val PLAYBACK_PROGRESS_THUMB_SIZE = 8.dp