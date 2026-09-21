package com.ssafy.motif.feature.capture.impl.challenge.shooting

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.motif.feature.capture.impl.R
import com.ssafy.motif.feature.capture.impl.component.CaptureRecordingTimer
import com.ssafy.motif.core.designsystem.R as DesignSystemR

@Composable
internal fun CaptureRecordingOverlay(
    elapsedTimeMs: Long,
    durationMs: Long,
    retryEnabled: Boolean,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier.fillMaxSize(),
    ) {
        CaptureRecordingTimer(
            elapsedTimeMs =
                elapsedTimeMs,
            durationMs =
                durationMs,
            modifier = Modifier
                .align(
                    Alignment.TopEnd,
                )
                .windowInsetsPadding(
                    WindowInsets.safeDrawing,
                )
                .padding(
                    top = 16.dp,
                    end = 16.dp,
                ),
        )

        CaptureRetryButton(
            enabled =
                retryEnabled,
            onClick =
                onRetryClick,
            modifier = Modifier
                .align(
                    Alignment.BottomEnd,
                )
                .windowInsetsPadding(
                    WindowInsets.safeDrawing,
                )
                .padding(
                    end = 16.dp,
                    bottom = 24.dp,
                ),
        )
    }
}

@Composable
private fun CaptureRetryButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource =
        remember {
            MutableInteractionSource()
        }

    Row(
        modifier = modifier
            .height(
                56.dp,
            )
            .clip(
                RoundedCornerShape(
                    4.dp,
                ),
            )
            .background(
                Color.Black.copy(
                    alpha =
                        if (enabled) {
                            0.78f
                        } else {
                            0.5f
                        },
                ),
            )
            .clickable(
                enabled =
                    enabled,
                interactionSource =
                    interactionSource,
                indication =
                    null,
                onClick =
                    onClick,
            )
            .padding(
                horizontal =
                    20.dp,
            ),
        verticalAlignment =
            Alignment.CenterVertically,
        horizontalArrangement =
            Arrangement.spacedBy(
                10.dp,
            ),
    ) {
        Image(
            painter =
                painterResource(
                    DesignSystemR.drawable
                        .ic_rotate_24,
                ),
            contentDescription =
                stringResource(
                    R.string.capture_retry,
                ),
            colorFilter =
                ColorFilter.tint(
                    Color.White,
                ),
            modifier =
                Modifier.size(
                    28.dp,
                ),
        )

        BasicText(
            text =
                stringResource(
                    R.string.capture_retry,
                ),
            style =
                TextStyle(
                    color =
                        Color.White,
                    fontSize =
                        20.sp,
                    fontWeight =
                        FontWeight.SemiBold,
                ),
        )
    }
}