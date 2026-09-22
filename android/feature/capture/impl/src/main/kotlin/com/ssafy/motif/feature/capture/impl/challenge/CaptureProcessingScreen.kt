package com.ssafy.motif.feature.capture.impl.challenge

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ssafy.motif.feature.capture.impl.R

@Composable
internal fun CaptureProcessingScreen(
    previewImageUrl: String,
    browseEnabled: Boolean,
    onBrowseChallengeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Color.Black,
            ),
    ) {
        if (
            previewImageUrl
                .isNotBlank()
        ) {
            AsyncImage(
                model =
                    previewImageUrl,
                contentDescription =
                    null,
                contentScale =
                    ContentScale.Crop,
                modifier =
                    Modifier.fillMaxSize(),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Color.Black.copy(
                        alpha = 0.62f,
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .align(
                    Alignment.Center,
                )
                .padding(
                    horizontal =
                        32.dp,
                ),
            horizontalAlignment =
                Alignment.CenterHorizontally,
        ) {
            BasicText(
                text =
                    stringResource(
                        R.string.capture_processing_title,
                    ),
                style =
                    TextStyle(
                        color =
                            Color.White,
                        fontSize =
                            24.sp,
                        lineHeight =
                            32.sp,
                        fontWeight =
                            FontWeight.Bold,
                        textAlign =
                            TextAlign.Center,
                    ),
            )

            Spacer(
                modifier =
                    Modifier.height(
                        28.dp,
                    ),
            )

            BasicText(
                text =
                    stringResource(
                        R.string.capture_processing_description,
                    ),
                style =
                    TextStyle(
                        color =
                            Color.White,
                        fontSize =
                            18.sp,
                        lineHeight =
                            32.sp,
                        fontWeight =
                            FontWeight.SemiBold,
                        textAlign =
                            TextAlign.Center,
                    ),
            )

            Spacer(
                modifier =
                    Modifier.height(
                        72.dp,
                    ),
            )

            CaptureBrowseChallengeButton(
                enabled =
                    browseEnabled,
                onClick =
                    onBrowseChallengeClick,
            )
        }
    }
}

@Composable
private fun CaptureBrowseChallengeButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource =
        remember {
            MutableInteractionSource()
        }

    Box(
        modifier = modifier
            .width(
                166.dp,
            )
            .height(
                56.dp,
            )
            .alpha(
                if (enabled) {
                    1f
                } else {
                    DisabledAlpha
                },
            )
            .clip(
                RoundedCornerShape(
                    28.dp,
                ),
            )
            .background(
                CapturePink,
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
            ),
        contentAlignment =
            Alignment.Center,
    ) {
        BasicText(
            text =
                stringResource(
                    R.string.capture_browse_challenge,
                ),
            style =
                TextStyle(
                    color =
                        Color.White,
                    fontSize =
                        18.sp,
                    fontWeight =
                        FontWeight.Medium,
                ),
        )
    }
}

private val CapturePink =
    Color(
        0xFFFF3B9D,
    )

private const val DisabledAlpha =
    0.45f