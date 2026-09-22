package com.ssafy.motif.feature.capture.impl.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.ssafy.motif.core.designsystem.component.Text
import com.ssafy.motif.core.designsystem.theme.MotifTheme

@Composable
internal fun CaptureCountdownOverlay(
    countdown: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier.fillMaxSize(),
        contentAlignment =
            Alignment.Center,
    ) {
        Text(
            text =
                countdown.toString(),
            style = MotifTheme.typography.titleB20.copy(
                fontSize = 96.sp
            ),
        )
    }
}