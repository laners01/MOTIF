package com.ssafy.motif.core.ui.video

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.ssafy.motif.core.designsystem.glass.GlassBackdropState
import com.ssafy.motif.core.designsystem.glass.glassBackdrop
import com.ssafy.motif.core.designsystem.glass.rememberGlassBackdropState

@Composable
internal fun VideoItem(
    thumbnailUrl: String,
    selected: Boolean,
    player: Player?,
    modifier: Modifier = Modifier,
    captureBackdrop: Boolean = false,
    decoration: @Composable BoxScope.(
        backdropState: GlassBackdropState,
    ) -> Unit = {},
) {
    val backdropState =
        rememberGlassBackdropState()

    Box(
        modifier = modifier,
    ) {
        if (selected && player != null) {
            VideoPlayer(
                player = player,
                thumbnailUrl = thumbnailUrl,
                modifier = Modifier
                    .fillMaxSize()
                    .let { baseModifier ->
                        if (captureBackdrop) {
                            baseModifier
                                .glassBackdrop(backdropState)
                        } else {
                            baseModifier
                        }
                    },
            )
        } else {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .let { baseModifier ->
                        if (captureBackdrop) {
                            baseModifier
                                .glassBackdrop(backdropState)
                        } else {
                            baseModifier
                        }
                    },
            )
        }

        decoration(backdropState)
    }
}