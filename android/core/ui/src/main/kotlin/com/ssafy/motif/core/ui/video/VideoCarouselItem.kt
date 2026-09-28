package com.ssafy.motif.core.ui.video

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.media3.common.Player
import com.ssafy.motif.core.designsystem.glass.GlassBackdropState
import kotlin.math.absoluteValue
import kotlin.math.sign

@Composable
internal fun VideoCarouselItem(
    thumbnailUrl: String,
    selected: Boolean,
    pageOffset: () -> Float,
    itemWidth: Dp,
    aspectRatio: Float,
    minScale: Float,
    player: Player?,
    captureBackdrop: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    decoration: @Composable BoxScope.(
        backdropState: GlassBackdropState,
    ) -> Unit = {},
) {
    VideoItem(
        thumbnailUrl = thumbnailUrl,
        selected = selected,
        player = player,
        captureBackdrop = captureBackdrop,
        modifier = modifier
            .width(itemWidth)
            .aspectRatio(aspectRatio)
            .graphicsLayer {

                val offset = pageOffset()

                val distance = offset
                    .absoluteValue
                    .coerceIn(0f, 1f)

                val scale = lerp(
                    start = minScale,
                    stop = 1f,
                    fraction = 1f - distance,
                )

                scaleX = scale
                scaleY = scale

                translationX =
                    offset.sign *
                            itemWidth.toPx() *
                            (1f - scale) /
                            2f
            }
            .clip(
                RoundedCornerShape(12.dp),
            )
            .clickable(
                onClick = onClick,
            ),
        decoration = decoration,
    )
}