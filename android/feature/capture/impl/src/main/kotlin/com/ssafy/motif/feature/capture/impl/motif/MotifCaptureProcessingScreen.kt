package com.ssafy.motif.feature.capture.impl.motif

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.core.model.motif.Asset
import com.ssafy.motif.feature.capture.impl.R
import com.ssafy.motif.feature.capture.impl.motif.model.MotifCaptureProcessingState

@Composable
internal fun MotifCaptureProcessingScreen(
    previewImageUrl: String,
    processingState: MotifCaptureProcessingState,
    uploadProgress: Int,
    recommendedAssets: List<Asset>,
    onAssetClick: (
        artistId: Long,
        assetId: Long,
    ) -> Unit,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black),
    ) {
        if (previewImageUrl.isNotBlank()) {
            AsyncImage(
                model = previewImageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Color.Black.copy(
                            alpha = BackgroundOverlayAlpha,
                        ),
                    ),
        )

        when (processingState) {
            MotifCaptureProcessingState.Idle,
            MotifCaptureProcessingState.PreparingUpload,
                -> {
                MotifProcessingMessage(
                    title =
                        stringResource(
                            R.string.capture_upload_preparing_title,
                        ),
                    description =
                        stringResource(
                            R.string.capture_upload_preparing_description,
                        ),
                    modifier =
                        Modifier.align(Alignment.Center),
                )
            }

            MotifCaptureProcessingState.Uploading -> {
                MotifUploadingContent(
                    progress = uploadProgress,
                    modifier =
                        Modifier.align(Alignment.Center),
                )
            }

            MotifCaptureProcessingState.RequestingComposite -> {
                MotifProcessingMessage(
                    title =
                        stringResource(
                            R.string.capture_requesting_title,
                        ),
                    description =
                        stringResource(
                            R.string.capture_requesting_description,
                        ),
                    modifier =
                        Modifier.align(Alignment.Center),
                )
            }

            MotifCaptureProcessingState.Queued -> {
                MotifQueuedContent(
                    recommendedAssets =
                        recommendedAssets,
                    onAssetClick = onAssetClick,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            MotifCaptureProcessingState.Failed -> {
                MotifFailedContent(
                    onRetryClick = onRetryClick,
                    modifier =
                        Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

@Composable
private fun MotifQueuedContent(
    recommendedAssets: List<Asset>,
    onAssetClick: (
        artistId: Long,
        assetId: Long,
    ) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier.padding(
                start = 48.dp,
                end = 48.dp,
                top = 44.dp,
                bottom = 34.dp,
            ),
        horizontalAlignment =
            Alignment.CenterHorizontally,
    ) {
        BasicText(
            text =
                stringResource(
                    R.string.motif_capture_processing_title,
                ),
            style = ProcessingTitleStyle,
        )

        Spacer(
            modifier = Modifier.height(26.dp),
        )

        BasicText(
            text =
                stringResource(
                    R.string.motif_capture_processing_browse,
                ),
            style = ProcessingDescriptionStyle,
        )

        Spacer(
            modifier = Modifier.height(24.dp),
        )

        BasicText(
            text =
                stringResource(
                    R.string.motif_capture_processing_notification,
                ),
            style = ProcessingDescriptionStyle,
        )

        Spacer(
            modifier = Modifier.weight(1f),
        )

        if (recommendedAssets.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(28.dp),
            ) {
                recommendedAssets
                    .take(MAX_RECOMMENDATION_COUNT)
                    .forEach { asset ->
                        MotifRecommendationCard(
                            asset = asset,
                            onClick = {
                                onAssetClick(
                                    asset.artist.id,
                                    asset.id,
                                )
                            },
                            modifier =
                                Modifier.weight(1f),
                        )
                    }
            }
        }
    }
}

@Composable
private fun MotifRecommendationCard(
    asset: Asset,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource =
        remember {
            MutableInteractionSource()
        }

    Column(
        modifier =
            modifier.clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    ) {
        AsyncImage(
            model = asset.thumbnailUrl,
            contentDescription = asset.title,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(138.dp)
                    .clip(
                        RoundedCornerShape(14.dp),
                    ),
        )

        Spacer(
            modifier = Modifier.height(8.dp),
        )

        BasicText(
            text = asset.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style =
                TextStyle(
                    color = Color.White,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Normal,
                ),
        )
    }
}

@Composable
private fun MotifUploadingContent(
    progress: Int,
    modifier: Modifier = Modifier,
) {
    val safeProgress =
        progress.coerceIn(
            MIN_PROGRESS,
            MAX_PROGRESS,
        )

    Column(
        modifier =
            modifier
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
            style = ProcessingTitleStyle,
        )

        Spacer(
            modifier = Modifier.height(20.dp),
        )

        BasicText(
            text =
                stringResource(
                    R.string.capture_upload_progress,
                    safeProgress,
                ),
            style =
                TextStyle(
                    color = Color.White,
                    fontSize = 32.sp,
                    lineHeight = 40.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                ),
        )

        Spacer(
            modifier = Modifier.height(24.dp),
        )

        MotifUploadProgressBar(
            progress = safeProgress,
        )

        Spacer(
            modifier = Modifier.height(20.dp),
        )

        BasicText(
            text =
                stringResource(
                    R.string.capture_upload_description,
                ),
            style = ProcessingDescriptionStyle,
        )
    }
}

@Composable
private fun MotifUploadProgressBar(
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
        modifier =
            modifier
                .width(ProgressBarWidth)
                .height(ProgressBarHeight)
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
            modifier =
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(
                        progressFraction,
                    )
                    .background(MotifTheme.colors.pink),
        )
    }
}

@Composable
private fun MotifProcessingMessage(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 32.dp,
                ),
        horizontalAlignment =
            Alignment.CenterHorizontally,
    ) {
        BasicText(
            text = title,
            style = ProcessingTitleStyle,
        )

        Spacer(
            modifier = Modifier.height(24.dp),
        )

        BasicText(
            text = description,
            style = ProcessingDescriptionStyle,
        )
    }
}

@Composable
private fun MotifFailedContent(
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
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
                    R.string.capture_upload_failed_title,
                ),
            style = ProcessingTitleStyle,
        )

        Spacer(
            modifier = Modifier.height(20.dp),
        )

        BasicText(
            text =
                stringResource(
                    R.string.capture_upload_failed_description,
                ),
            style = ProcessingDescriptionStyle,
        )

        Spacer(
            modifier = Modifier.height(36.dp),
        )

        MotifRetryTransferButton(
            onClick = onRetryClick,
        )
    }
}

@Composable
private fun MotifRetryTransferButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource =
        remember {
            MutableInteractionSource()
        }

    Box(
        modifier =
            modifier
                .height(52.dp)
                .clip(
                    RoundedCornerShape(14.dp),
                )
                .background(MotifTheme.colors.pink)
                .clickable(
                    interactionSource =
                        interactionSource,
                    indication = null,
                    onClick = onClick,
                )
                .padding(
                    horizontal = 28.dp,
                ),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text =
                stringResource(
                    R.string.motif_capture_retry,
                ),
            style =
                TextStyle(
                    color = Color.White,
                    fontSize = 18.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Medium,
                ),
        )
    }
}

private val ProcessingTitleStyle =
    TextStyle(
        color = Color.White,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )

private val ProcessingDescriptionStyle =
    TextStyle(
        color = Color.White,
        fontSize = 18.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
    )


private val ProgressBarWidth = 260.dp
private val ProgressBarHeight = 8.dp

private const val MIN_PROGRESS = 0
private const val MAX_PROGRESS = 100
private const val MAX_RECOMMENDATION_COUNT = 3

private const val BackgroundOverlayAlpha = 0.62f
private const val ProgressBarBackgroundAlpha = 0.24f
