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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import com.ssafy.motif.core.designsystem.component.Text
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.core.model.artist.Artist
import com.ssafy.motif.core.model.challenge.CapturePosition
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
    BoxWithConstraints(
        modifier =
            modifier.fillMaxSize(),
    ) {
        CapturePreviewImage(
            imageUrl =
                uiState.previewImageUrl,
            modifier =
                Modifier.fillMaxSize(),
        )

        val maxProfileX =
            (
                    maxWidth -
                            CaptureProfileSize
                    )
                .coerceAtLeast(
                    0.dp,
                )

        val maxProfileY =
            (
                    maxHeight -
                            CaptureProfileSize
                    )
                .coerceAtLeast(
                    0.dp,
                )

        uiState.positions
            .forEach { position ->
                val x =
                    position.x
                        ?: return@forEach

                val y =
                    position.y
                        ?: return@forEach

                val isSelected =
                    position.id ==
                            uiState.selectedParticipantId

                CapturePositionMarker(
                    position =
                        position,
                    selected =
                        isSelected,
                    enabled =
                        position.isAvailable,
                    onClick = {
                        onPositionClick(
                            position.id,
                        )
                    },
                    modifier =
                        Modifier.offset(
                            x =
                                (
                                        maxWidth *
                                                x.coerceIn(
                                                    0f,
                                                    1f,
                                                ) -
                                                CaptureProfileSize /
                                                2
                                        )
                                    .coerceIn(
                                        minimumValue =
                                            0.dp,
                                        maximumValue =
                                            maxProfileX,
                                    ),
                            y =
                                (
                                        maxHeight *
                                                y.coerceIn(
                                                    0f,
                                                    1f,
                                                ) -
                                                CaptureProfileSize /
                                                2
                                        )
                                    .coerceIn(
                                        minimumValue =
                                            0.dp,
                                        maximumValue =
                                            maxProfileY,
                                    ),
                        ),
                )
            }

        CapturePositionGuide(
            modifier =
                Modifier
                    .align(
                        Alignment.TopCenter,
                    )
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing,
                    )
                    .padding(
                        start = 16.dp,
                        top = 60.dp,
                        end = 16.dp,
                    ),
        )

        StartChallengeButton(
            enabled =
                uiState.canStartChallenge,
            onClick =
                onStartChallengeClick,
            modifier =
                Modifier
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
    /*
     * 두 개의 이미지 레이어를 번갈아 사용한다.
     *
     * 현재 보이는 이미지는 그대로 유지하고,
     * 반대쪽 레이어에서 새 이미지를 먼저 로드한다.
     *
     * 새 이미지의 로딩이 성공한 뒤에만
     * visible layer를 변경하므로 이미지 변경 시
     * 검은 화면이 노출되지 않는다.
     */
    var primaryImageUrl by
    remember {
        mutableStateOf(
            imageUrl,
        )
    }

    var secondaryImageUrl by
    remember {
        mutableStateOf<String?>(
            null,
        )
    }

    var showPrimaryImage by
    remember {
        mutableStateOf(
            true,
        )
    }

    LaunchedEffect(
        imageUrl,
    ) {
        if (
            imageUrl.isBlank()
        ) {
            return@LaunchedEffect
        }

        val displayedImageUrl =
            if (
                showPrimaryImage
            ) {
                primaryImageUrl
            } else {
                secondaryImageUrl
            }

        /*
         * 이미 현재 화면에 표시 중인 이미지라면
         * 다시 로드하지 않는다.
         */
        if (
            displayedImageUrl ==
            imageUrl
        ) {
            return@LaunchedEffect
        }

        /*
         * 현재 보이지 않는 레이어에
         * 다음 이미지를 먼저 로드한다.
         */
        if (
            showPrimaryImage
        ) {
            secondaryImageUrl =
                imageUrl
        } else {
            primaryImageUrl =
                imageUrl
        }
    }

    Box(
        modifier =
            modifier.background(
                Color.Gray,
            ),
    ) {
        if (
            primaryImageUrl.isNotBlank()
        ) {
            AsyncImage(
                model =
                    primaryImageUrl,
                contentDescription =
                    null,
                contentScale =
                    ContentScale.Crop,
                onSuccess = {
                    /*
                     * Primary가 새로 로딩 중인 레이어였고
                     * 현재 필요한 이미지가 맞다면,
                     * 성공한 순간 화면에 노출한다.
                     */
                    if (
                        !showPrimaryImage &&
                        primaryImageUrl ==
                        imageUrl
                    ) {
                        showPrimaryImage =
                            true
                    }
                },
                modifier =
                    Modifier
                        .fillMaxSize()
                        .alpha(
                            if (
                                showPrimaryImage
                            ) {
                                1f
                            } else {
                                0f
                            },
                        ),
            )
        }

        secondaryImageUrl
            ?.takeIf { url ->
                url.isNotBlank()
            }
            ?.let { url ->
                AsyncImage(
                    model =
                        url,
                    contentDescription =
                        null,
                    contentScale =
                        ContentScale.Crop,
                    onSuccess = {
                        /*
                         * Secondary가 새로 로딩 중인 레이어였고
                         * 현재 필요한 이미지가 맞다면,
                         * 성공한 순간 화면에 노출한다.
                         */
                        if (
                            showPrimaryImage &&
                            url ==
                            imageUrl
                        ) {
                            showPrimaryImage =
                                false
                        }
                    },
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .alpha(
                                if (
                                    showPrimaryImage
                                ) {
                                    0f
                                } else {
                                    1f
                                },
                            ),
                )
            }
    }
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
            modifier
                .size(
                    CaptureProfileSize,
                )
                .alpha(
                    if (
                        enabled
                    ) {
                        1f
                    } else {
                        DisabledAlpha
                    },
                ),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .clip(
                        CircleShape,
                    )
                    .background(
                        if (
                            selected
                        ) {
                            CaptureAccentColor
                        } else {
                            Color.White
                        },
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
                    )
                    .padding(
                        if (
                            selected
                        ) {
                            CaptureSelectedBorderWidth
                        } else {
                            CaptureDefaultBorderWidth
                        },
                    ),
        ) {
            AsyncImage(
                model =
                    position
                        .artist
                        .profileImageUrl,
                contentDescription =
                    position.artist.name,
                contentScale =
                    ContentScale.Crop,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(
                            CircleShape,
                        )
                        .background(
                            Color.DarkGray,
                        ),
            )
        }

        if (
            selected
        ) {
            CaptureHereBadge(
                modifier =
                    Modifier
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
        modifier =
            modifier
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
                    color =
                        Color.White,
                    fontSize =
                        12.sp,
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
        modifier =
            modifier
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
        Text(
            text = stringResource(R.string.capture_select_position),
            style = MotifTheme.typography.titleSB18,
            color = MotifTheme.colors.white
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
        modifier =
            modifier
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
                            if (
                                enabled
                            ) {
                                0.72f
                            } else {
                                0.4f
                            },
                    ),
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

private const val DisabledAlpha =
    0.45f

@Preview(
    showBackground = true,
    widthDp = 393,
    heightDp = 852,
)
@Composable
private fun CapturePositionSelectionPreview() {
    val context =
        LocalContext.current

    val previewImageUrl =
        "android.resource://" +
                "${context.packageName}/" +
                "${R.drawable.img_test_capture_select}"

    val profileImageUrl =
        "android.resource://" +
                "${context.packageName}/" +
                "${DesignSystemR.drawable.img_profile}"

    var selectedParticipantId by
    remember {
        mutableLongStateOf(
            31L,
        )
    }

    val positions =
        listOf(
            CapturePosition(
                id = 31L,
                artist =
                    Artist(
                        id = 7L,
                        name = "윈터",
                        profileImageUrl =
                            profileImageUrl,
                        groupName =
                            "aespa",
                    ),
                slotOrder = 0,
                x = 0.22f,
                y = 0.36f,
                initialSceneUrl =
                    previewImageUrl,
                previewImageUrl =
                    previewImageUrl,
            ),
            CapturePosition(
                id = 32L,
                artist =
                    Artist(
                        id = 9L,
                        name = "닝닝",
                        profileImageUrl =
                            profileImageUrl,
                        groupName =
                            "aespa",
                    ),
                slotOrder = 1,
                x = 0.72f,
                y = 0.34f,
                initialSceneUrl =
                    null,
                previewImageUrl =
                    null,
            ),
        )

    CapturePositionSelectionScreen(
        uiState =
            CaptureUiState(
                challengeId = 11L,
                challengeThumbnailUrl =
                    previewImageUrl,
                positions =
                    positions,
                selectedParticipantId =
                    selectedParticipantId,
            ),
        onPositionClick = { id ->
            selectedParticipantId =
                id
        },
        onStartChallengeClick = {},
    )
}