package com.ssafy.motif.feature.capture.impl.challenge

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ssafy.motif.core.model.challenge.CapturePosition
import com.ssafy.motif.core.model.artist.Artist
import com.ssafy.motif.feature.capture.impl.R
import com.ssafy.motif.feature.capture.impl.challenge.model.CaptureUiState
import com.ssafy.motif.core.designsystem.R as DesignSystemR

@Composable
internal fun CapturePositionSelectionScreen(
    uiState: CaptureUiState,
    onPositionClick: (Long) -> Unit,
    onStartChallengeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val previewImageUrl =
        uiState.selectedPosition
            ?.previewImageUrl
            .orEmpty()

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
    ) {
        CapturePreviewImage(
            imageUrl = previewImageUrl,
            modifier = Modifier.fillMaxSize(),
        )

        val maxProfileX =
            (maxWidth - CaptureProfileSize)
                .coerceAtLeast(0.dp)

        val maxProfileY =
            (maxHeight - CaptureProfileSize)
                .coerceAtLeast(0.dp)

        uiState.positions.forEach { position ->
            val artistId =
                position.artist.id

            val isOriginalArtist =
                artistId ==
                        uiState.originalArtistId

            val isSelectedPosition =
                artistId ==
                        uiState.selectedPositionArtistId

            CapturePositionMarker(
                position = position,
                selected = isSelectedPosition,
                enabled = !isOriginalArtist,
                onClick = {
                    onPositionClick(
                        artistId,
                    )
                },
                modifier = Modifier.offset(
                    x = (
                            maxWidth *
                                    position.x.coerceIn(
                                        0f,
                                        1f,
                                    ) -
                                    CaptureProfileSize / 2
                            )
                        .coerceIn(
                            minimumValue = 0.dp,
                            maximumValue = maxProfileX,
                        ),
                    y = (
                            maxHeight *
                                    position.y.coerceIn(
                                        0f,
                                        1f,
                                    ) -
                                    CaptureProfileSize / 2
                            )
                        .coerceIn(
                            minimumValue = 0.dp,
                            maximumValue = maxProfileY,
                        ),
                ),
            )
        }

        CapturePositionGuide(
            modifier = Modifier
                .align(
                    Alignment.TopCenter,
                )
                .windowInsetsPadding(
                    WindowInsets.safeDrawing,
                )
                .padding(
                    start = 16.dp,
                    top = 88.dp,
                    end = 16.dp,
                ),
        )

        StartChallengeButton(
            enabled =
                uiState.selectedPosition != null,
            onClick =
                onStartChallengeClick,
            modifier = Modifier
                .align(
                    Alignment.BottomCenter,
                )
                .windowInsetsPadding(
                    WindowInsets.safeDrawing,
                )
                .padding(
                    start = 48.dp,
                    end = 48.dp,
                    bottom = 32.dp,
                ),
        )
    }
}

@Composable
private fun CapturePreviewImage(
    imageUrl: String,
    modifier: Modifier = Modifier,
) {
    if (imageUrl.isBlank()) {
        Box(
            modifier =
                modifier.background(
                    Color.Gray,
                ),
        )

        return
    }

    AsyncImage(
        model = imageUrl,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}

@Composable
private fun CapturePositionMarker(
    position: CapturePosition,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource =
        remember {
            MutableInteractionSource()
        }

    Box(
        modifier =
            modifier.size(
                CaptureProfileSize,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(
                    CircleShape,
                )
                .background(
                    if (selected) {
                        CaptureAccentColor
                    } else {
                        Color.White
                    },
                )
                .clickable(
                    enabled = enabled,
                    interactionSource =
                        interactionSource,
                    indication = null,
                    onClick = onClick,
                )
                .padding(
                    if (selected) {
                        CaptureSelectedBorderWidth
                    } else {
                        CaptureDefaultBorderWidth
                    },
                ),
        ) {
            AsyncImage(
                model =
                    position.artist.profileImageUrl,
                contentDescription =
                    position.artist.name,
                contentScale =
                    ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(
                        CircleShape,
                    )
                    .background(
                        Color.DarkGray,
                    ),
            )
        }

        if (selected) {
            CaptureHereBadge(
                modifier = Modifier
                    .align(
                        Alignment.TopCenter,
                    )
                    .offset(
                        y = (-14).dp,
                    ),
            )
        }
    }
}

@Composable
private fun CaptureHereBadge(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(
                RoundedCornerShape(
                    12.dp,
                ),
            )
            .background(
                CaptureAccentColor,
            )
            .padding(
                horizontal = 10.dp,
                vertical = 4.dp,
            ),
        contentAlignment =
            Alignment.Center,
    ) {
        BasicText(
            text =
                stringResource(
                    R.string.capture_here,
                ),
            style =
                TextStyle(
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight =
                        FontWeight.Bold,
                    textAlign =
                        TextAlign.Center,
                ),
        )
    }
}

@Composable
private fun CapturePositionGuide(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(
                56.dp,
            )
            .clip(
                RoundedCornerShape(
                    20.dp,
                ),
            )
            .background(
                Color.Black.copy(
                    alpha = 0.68f,
                ),
            )
            .padding(
                horizontal = 20.dp,
            ),
        contentAlignment =
            Alignment.Center,
    ) {
        BasicText(
            text =
                stringResource(
                    R.string.capture_select_position,
                ),
            style =
                TextStyle(
                    color =
                        Color.White,
                    fontSize =
                        18.sp,
                    fontWeight =
                        FontWeight.SemiBold,
                    textAlign =
                        TextAlign.Center,
                ),
        )
    }
}

@Composable
private fun StartChallengeButton(
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
            .fillMaxWidth()
            .height(
                64.dp,
            )
            .clip(
                RoundedCornerShape(
                    18.dp,
                ),
            )
            .background(
                Color.Black.copy(
                    alpha =
                        if (enabled) {
                            0.72f
                        } else {
                            0.4f
                        },
                ),
            )
            .clickable(
                enabled = enabled,
                interactionSource =
                    interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment =
            Alignment.Center,
    ) {
        BasicText(
            text =
                stringResource(
                    R.string.capture_start_challenge,
                ),
            style =
                TextStyle(
                    color =
                        Color.White,
                    fontSize =
                        22.sp,
                    fontWeight =
                        FontWeight.SemiBold,
                    textAlign =
                        TextAlign.Center,
                ),
        )
    }
}

private val CaptureProfileSize =
    76.dp

private val CaptureSelectedBorderWidth =
    5.dp

private val CaptureDefaultBorderWidth =
    4.dp

private val CaptureAccentColor =
    Color(
        0xFFFF3B9D,
    )

@Preview(
    showBackground = true,
    widthDp = 393,
    heightDp = 852,
)
@Composable
private fun CaptureThreePersonPositionSelectionPreview() {
    val originalArtistId =
        1L

    var selectedArtistId
            by remember {
                mutableLongStateOf(
                    2L,
                )
            }

    val context =
        LocalContext.current

    val selectImageUrl =
        "android.resource://" +
                "${context.packageName}/" +
                "${R.drawable.img_test_capture_select}"

    val artistProfileImageUrl =
        "android.resource://" +
                "${context.packageName}/" +
                "${DesignSystemR.drawable.img_profile}"

    val positions =
        remember(
            selectImageUrl,
            artistProfileImageUrl,
        ) {
            listOf(
                CapturePosition(
                    artist = Artist(
                        id = 1L,
                        name = "Original Artist",
                        profileImageUrl =
                            artistProfileImageUrl,
                        groupName = "MOTIF",
                    ),
                    x = 0.18f,
                    y = 0.35f,
                    previewImageUrl = "",
                    videoUrl = "",
                ),
                CapturePosition(
                    artist = Artist(
                        id = 2L,
                        name = "Position Artist 1",
                        profileImageUrl =
                            artistProfileImageUrl,
                        groupName = "MOTIF",
                    ),
                    x = 0.50f,
                    y = 0.30f,
                    previewImageUrl =
                        selectImageUrl,
                    videoUrl = "",
                ),
                CapturePosition(
                    artist = Artist(
                        id = 3L,
                        name = "Position Artist 2",
                        profileImageUrl =
                            artistProfileImageUrl,
                        groupName = "MOTIF",
                    ),
                    x = 0.80f,
                    y = 0.36f,
                    previewImageUrl =
                        selectImageUrl,
                    videoUrl = "",
                ),
            )
        }

    CapturePositionSelectionScreen(
        uiState =
            CaptureUiState(
                originalArtistId =
                    originalArtistId,
                positions =
                    positions,
                selectedPositionArtistId =
                    selectedArtistId,
            ),
        onPositionClick = { artistId ->
            if (
                artistId !=
                originalArtistId
            ) {
                selectedArtistId =
                    artistId
            }
        },
        onStartChallengeClick = {},
    )
}