package com.ssafy.motif.core.designsystem.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot

@Stable
class GlassBackdropState internal constructor(
    internal val graphicsLayer: GraphicsLayer,
) {
    internal var positionInRoot by mutableStateOf(Offset.Zero)
}

@Composable
fun rememberGlassBackdropState(): GlassBackdropState {
    val graphicsLayer = rememberGraphicsLayer()

    return remember(graphicsLayer) {
        GlassBackdropState(
            graphicsLayer = graphicsLayer,
        )
    }
}

fun Modifier.glassBackdrop(
    state: GlassBackdropState,
): Modifier =
    this
        .onGloballyPositioned { coordinates ->
            state.positionInRoot = coordinates.positionInRoot()
        }
        .drawWithContent {
            state.graphicsLayer.record {
                this@drawWithContent.drawContent()
            }

            drawLayer(state.graphicsLayer)
        }