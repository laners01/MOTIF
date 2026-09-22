package com.ssafy.motif.feature.capture.impl.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.motif.core.designsystem.component.Text
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import java.util.Locale

@Composable
internal fun CaptureRecordingTimer(
    elapsedTimeMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(30.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(8.dp),
        verticalAlignment =
            Alignment.CenterVertically,
        horizontalArrangement =
            Arrangement.spacedBy(
                10.dp,
            ),
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color = MotifTheme.colors.pink),
        )

        Text(
            text = formatTime(elapsedTimeMs),
            color = MotifTheme.colors.white,
            style = MotifTheme.typography.bodyR12
        )

        Text(
            text = "/",
            color = MotifTheme.colors.white,
            style = MotifTheme.typography.bodyR12
        )

        Text(
            text = formatTime(durationMs),
            color = MotifTheme.colors.gray500,
            style = MotifTheme.typography.bodyR12
        )
    }
}

private fun formatTime(
    timeMs: Long,
): String {
    val totalSeconds =
        (
                timeMs /
                        1_000L
                )
            .coerceAtLeast(
                0L,
            )

    val minutes =
        totalSeconds /
                60L

    val seconds =
        totalSeconds %
                60L

    return String.format(
        Locale.US,
        "%02d:%02d",
        minutes,
        seconds,
    )
}

private val TimerCurrentTextStyle =
    TextStyle(
        color =
            Color.White,
        fontSize =
            17.sp,
        fontWeight =
            FontWeight.SemiBold,
    )

private val TimerSecondaryTextStyle =
    TextStyle(
        color =
            Color(
                0xFFB9B9B9,
            ),
        fontSize =
            17.sp,
        fontWeight =
            FontWeight.SemiBold,
    )

private val CapturePink =
    Color(
        0xFFFF3B9D,
    )