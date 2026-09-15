package com.ssafy.motif.feature.home.impl.component.notice

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal object NoticeDefaults {
    val CollapsedHeight = 512.dp
    val Shape = RoundedCornerShape(12.dp)

    val AccentColor = Color(0xFFFB44A5)

    val ShadowColor = Color.White.copy(
        alpha = 0.16f,
    )

    val BorderColor = Color.White.copy(
        alpha = 0.06f,
    )
}