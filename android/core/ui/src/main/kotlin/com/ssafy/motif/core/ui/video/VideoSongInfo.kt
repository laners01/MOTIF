package com.ssafy.motif.core.ui.video

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ssafy.motif.core.designsystem.component.GlassSurface
import com.ssafy.motif.core.designsystem.component.Icon
import com.ssafy.motif.core.designsystem.component.Text
import com.ssafy.motif.core.designsystem.glass.GlassBackdropState
import com.ssafy.motif.core.designsystem.icon.MotifIcons
import com.ssafy.motif.core.designsystem.theme.MotifColors

@Composable
fun VideoSongInfo(
    artistName: String,
    songTitle: String,
    backdropState: GlassBackdropState,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val interactionSource =
        remember {
            MutableInteractionSource()
        }

    GlassSurface(
        backdropState =
            backdropState,
        modifier = modifier
            .wrapContentWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource =
                            interactionSource,
                        indication =
                            null,
                        onClick =
                            onClick,
                    )
                } else {
                    Modifier
                },
            ),
        shape =
            RoundedCornerShape(
                12.dp,
            ),
        tintColor =
            MotifColors
                .defaultColors()
                .white
                .copy(
                    alpha = 0.5f,
                ),
        contentPadding =
            PaddingValues(
                horizontal = 12.dp,
                vertical = 6.dp,
            ),
    ) {
        Row(
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            Icon(
                painter =
                    painterResource(
                        MotifIcons.FilledPlay,
                    ),
                contentDescription = null,
                modifier =
                    Modifier.size(
                        16.dp,
                    ),
            )

            Spacer(
                modifier =
                    Modifier.width(
                        8.dp,
                    ),
            )

            Text(
                text =
                    "$artistName · $songTitle",
                maxLines = 1,
                overflow =
                    TextOverflow.Ellipsis,
            )
        }
    }
}