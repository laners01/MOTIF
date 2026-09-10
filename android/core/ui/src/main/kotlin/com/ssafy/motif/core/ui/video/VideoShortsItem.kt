package com.ssafy.motif.core.ui.video

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.media3.common.Player
import com.ssafy.motif.core.designsystem.glass.GlassBackdropState

@Composable
internal fun VideoShortsItem(
    thumbnailUrl: String,
    selected: Boolean,
    player: Player?,
    captureBackdrop: Boolean,
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
        modifier = modifier.fillMaxSize(),
        decoration = decoration,
    )
}