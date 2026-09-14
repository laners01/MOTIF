package com.ssafy.motif.core.designsystem.component.bottomsheet

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.ssafy.motif.core.designsystem.theme.MotifTheme

object BottomSheetDefaults {

    const val PARTIALLY_EXPANDED_FRACTION = 0.6f

    val ExpandedShape: Shape =
        RoundedCornerShape(
            topStart = 28.dp,
            topEnd = 28.dp,
        )

    val ScrimColor: Color =
        Color.Black.copy(
            alpha = 0.45f,
        )

    internal val AnimationSpec: FiniteAnimationSpec<Float> =
        tween(
            durationMillis = 300,
            easing = FastOutSlowInEasing,
        )

    @Composable
    fun DragHandle(
        modifier: Modifier = Modifier,
        color: Color = MotifTheme.colors.gray500,
    ) {
        Box(
            modifier = modifier
                .width(56.dp)
                .height(4.dp)
                .background(
                    color = color,
                    shape = CircleShape,
                ),
        )
    }
}