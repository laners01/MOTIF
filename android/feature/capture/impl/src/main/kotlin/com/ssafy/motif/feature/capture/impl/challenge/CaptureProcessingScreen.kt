package com.ssafy.motif.feature.capture.impl.challenge

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.ssafy.motif.feature.capture.impl.challenge.model.CaptureProcessingState

@Composable
internal fun CaptureProcessingScreen(
    previewImageUrl: String,
    processingState: CaptureProcessingState,
    uploadProgress: Int,
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
                        alpha =
                            BackgroundOverlayAlpha,
                    ),
                ),
        )

        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            when (
                processingState
            ) {
                CaptureProcessingState.Idle,
                CaptureProcessingState.PreparingUpload,
                    -> {
                    CaptureProcessingContent(
                        title =
                            stringResource(
                                R.string.capture_upload_preparing_title,
                            ),
                        description =
                            stringResource(
                                R.string.capture_upload_preparing_description,
                            ),
                    )
                }

                CaptureProcessingState.Uploading -> {
                    CaptureUploadingContent(
                        progress =
                            uploadProgress,
                    )
                }

                CaptureProcessingState.RequestingComposite -> {
                    CaptureProcessingContent(
                        title =
                            stringResource(
                                R.string.capture_requesting_title,
                            ),
                        description =
                            stringResource(
                                R.string.capture_requesting_description,
                            ),
                    )
                }

                CaptureProcessingState.Queued -> {
                    CaptureProcessingContent(
                        title =
                            stringResource(
                                R.string.capture_processing_title,
                            ),
                        description =
                            stringResource(
                                R.string.capture_processing_description,
                            ),
                        onBrowseChallengeClick =
                            onBrowseChallengeClick,
                    )
                }

                CaptureProcessingState.Failed -> {
                    CaptureProcessingContent(
                        title =
                            stringResource(
                                R.string.capture_upload_failed_title,
                            ),
                        description =
                            stringResource(
                                R.string.capture_upload_failed_description,
                            ),
                        onBrowseChallengeClick =
                            onBrowseChallengeClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun CaptureUploadingContent(
    progress: Int,
    modifier: Modifier = Modifier,
) {
    val safeProgress =
        progress.coerceIn(
            MIN_PROGRESS,
            MAX_PROGRESS,
        )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = 32.dp,
            ),
        horizontalAlignment =
            Alignment.CenterHorizontally,
    ) {
        BasicText(
            text =
                stringResource(
                    R.string.capture_upload_title,
                ),
            style =
                CaptureTitleStyle,
        )

        Spacer(
            modifier =
                Modifier.height(
                    20.dp,
                ),
        )

        BasicText(
            text =
                stringResource(
                    R.string.capture_upload_progress,
                    safeProgress,
                ),
            style =
                TextStyle(
                    color =
                        Color.White,
                    fontSize =
                        32.sp,
                    lineHeight =
                        40.sp,
                    fontWeight =
                        FontWeight.Bold,
                    textAlign =
                        TextAlign.Center,
                ),
        )

        Spacer(
            modifier =
                Modifier.height(
                    24.dp,
                ),
        )

        CaptureUploadProgressBar(
            progress =
                safeProgress,
        )

        Spacer(
            modifier =
                Modifier.height(
                    20.dp,
                ),
        )

        BasicText(
            text =
                stringResource(
                    R.string.capture_upload_description,
                ),
            style =
                CaptureDescriptionStyle,
        )
    }
}

@Composable
private fun CaptureUploadProgressBar(
    progress: Int,
    modifier: Modifier = Modifier,
) {
    val progressFraction =
        progress
            .coerceIn(
                MIN_PROGRESS,
                MAX_PROGRESS,
            )
            .toFloat() /
                MAX_PROGRESS

    Box(
        modifier = modifier
            .width(
                ProgressBarWidth,
            )
            .height(
                ProgressBarHeight,
            )
            .clip(
                RoundedCornerShape(
                    ProgressBarHeight / 2,
                ),
            )
            .background(
                Color.White.copy(
                    alpha =
                        ProgressBarBackgroundAlpha,
                ),
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(
                    progressFraction,
                )
                .background(
                    CapturePink,
                ),
        )
    }
}

@Composable
private fun CaptureProcessingContent(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    onBrowseChallengeClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = 32.dp,
            ),
        horizontalAlignment =
            Alignment.CenterHorizontally,
    ) {
        BasicText(
            text =
                title,
            style =
                CaptureTitleStyle,
        )

        Spacer(
            modifier =
                Modifier.height(
                    28.dp,
                ),
        )

        BasicText(
            text =
                description,
            style =
                CaptureDescriptionStyle,
        )

        if (
            onBrowseChallengeClick !=
            null
        ) {
            Spacer(
                modifier =
                    Modifier.height(
                        72.dp,
                    ),
            )

            CaptureBrowseChallengeButton(
                onClick =
                    onBrowseChallengeClick,
            )
        }
    }
}

@Composable
private fun CaptureBrowseChallengeButton(
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
            .clip(
                RoundedCornerShape(
                    28.dp,
                ),
            )
            .background(
                CapturePink,
            )
            .clickable(
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

private val CaptureTitleStyle =
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
    )

private val CaptureDescriptionStyle =
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
    )

private val CapturePink =
    Color(
        0xFFFF3B9D,
    )

private val ProgressBarWidth =
    260.dp

private val ProgressBarHeight =
    8.dp

private const val MIN_PROGRESS = 0
private const val MAX_PROGRESS = 100

private const val BackgroundOverlayAlpha =
    0.62f

private const val ProgressBarBackgroundAlpha =
    0.24f