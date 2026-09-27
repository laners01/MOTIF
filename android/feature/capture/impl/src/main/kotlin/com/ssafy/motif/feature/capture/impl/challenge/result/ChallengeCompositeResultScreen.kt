package com.ssafy.motif.feature.capture.impl.challenge.result

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.ssafy.motif.core.designsystem.component.LoadingWheel
import com.ssafy.motif.core.designsystem.icon.MotifIcons
import com.ssafy.motif.core.model.composite.CompositeJobStatus
import com.ssafy.motif.feature.capture.impl.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun ChallengeCompositeResultScreen(
    jobUuid: String,
    onBackClick: () -> Unit,
    onPublished: (Long) -> Unit,
    onError: (Throwable) -> Unit,
    modifier: Modifier = Modifier,
    viewModel:
    ChallengeCompositeResultViewModel =
        hiltViewModel(),
) {
    val uiState by
    viewModel.uiState
        .collectAsStateWithLifecycle()

    LaunchedEffect(
        jobUuid,
    ) {
        viewModel.initialize(
            jobUuid =
                jobUuid,
        )
    }

    LaunchedEffect(
        viewModel,
    ) {
        viewModel.uiEffect
            .collect { effect ->
                when (effect) {
                    is ChallengeCompositeResultEffect
                    .Published -> {
                        onPublished(
                            effect.videoId,
                        )
                    }

                    ChallengeCompositeResultEffect
                        .Close -> {
                        onBackClick()
                    }

                    is ChallengeCompositeResultEffect
                    .Error -> {
                        onError(
                            effect.throwable,
                        )
                    }
                }
            }
    }

    ChallengeCompositeResultScreen(
        uiState =
            uiState,
        onBackClick =
            onBackClick,
        onUploadClick =
            viewModel::showUploadDialog,
        onDeleteClick =
            viewModel::showDeleteDialog,
        onDismissDialog =
            viewModel::dismissDialog,
        onDescriptionChange =
            viewModel::updateDescription,
        onVisibilityClick =
            viewModel::toggleVisibility,
        onCommentEnabledClick =
            viewModel::toggleCommentEnabled,
        onPublishClick =
            viewModel::publish,
        onDeleteConfirm =
            viewModel::discardResult,
        onRetryComposite =
            viewModel::retryComposite,
        onReload =
            viewModel::loadCompositeJob,
        modifier =
            modifier,
    )
}

@Composable
private fun ChallengeCompositeResultScreen(
    uiState: ChallengeCompositeResultUiState,
    onBackClick: () -> Unit,
    onUploadClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onDismissDialog: () -> Unit,
    onDescriptionChange: (String) -> Unit,
    onVisibilityClick: () -> Unit,
    onCommentEnabledClick: () -> Unit,
    onPublishClick: () -> Unit,
    onDeleteConfirm: () -> Unit,
    onRetryComposite: () -> Unit,
    onReload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val job =
        uiState.compositeJob

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(
                    Color.Black,
                ),
    ) {
        when {
            job?.status ==
                    CompositeJobStatus.DONE &&
                    !job.resultVideoUrl.isNullOrBlank() -> {
                ResultVideoPlayer(
                    videoUrl = job.resultVideoUrl!!,
                    modifier =
                        Modifier.fillMaxSize(),
                )
            }

            job?.status ==
                    CompositeJobStatus.FAILED -> {
                CompositeFailedContent(
                    failCode =
                        job.failCode,
                    onRetryClick =
                        onRetryComposite,
                    modifier =
                        Modifier.fillMaxSize(),
                )
            }

            job != null -> {
                CompositeProcessingContent(
                    modifier =
                        Modifier.fillMaxSize(),
                )
            }

            uiState.error != null -> {
                CompositeLoadFailedContent(
                    onRetryClick =
                        onReload,
                    modifier =
                        Modifier.fillMaxSize(),
                )
            }
        }

        if (
            job?.status ==
            CompositeJobStatus.DONE
        ) {
            ResultBottomActions(
                onUploadClick =
                    onUploadClick,
                onDeleteClick =
                    onDeleteClick,
                modifier =
                    Modifier
                        .align(
                            Alignment.BottomCenter,
                        )
                        .windowInsetsPadding(
                            WindowInsets.navigationBars,
                        )
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            bottom = 20.dp,
                        ),
            )
        }

        ResultBackButton(
            onClick =
                onBackClick,
            modifier =
                Modifier
                    .align(
                        Alignment.TopStart,
                    )
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing,
                    )
                    .padding(
                        start = 12.dp,
                        top = 8.dp,
                    ),
        )

        if (
            uiState.isLoading ||
            uiState.isPublishing
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            Color.Black.copy(
                                alpha = 0.55f,
                            ),
                        ),
                contentAlignment =
                    Alignment.Center,
            ) {
                LoadingWheel()
            }
        }

        when (
            uiState.dialog
        ) {
            CompositeResultDialog.None ->
                Unit

            CompositeResultDialog.Upload -> {
                ChallengeUploadOverlay(
                    description =
                        uiState.description,
                    isPublic =
                        uiState.isPublic,
                    commentEnabled =
                        uiState.commentEnabled,
                    onDescriptionChange =
                        onDescriptionChange,
                    onVisibilityClick =
                        onVisibilityClick,
                    onCommentEnabledClick =
                        onCommentEnabledClick,
                    onCancelClick =
                        onDismissDialog,
                    onConfirmClick =
                        onPublishClick,
                )
            }

            CompositeResultDialog.Delete -> {
                ChallengeDeleteOverlay(
                    onCancelClick =
                        onDismissDialog,
                    onConfirmClick =
                        onDeleteConfirm,
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun ResultVideoPlayer(
    videoUrl: String,
    modifier: Modifier = Modifier,
) {
    val context =
        LocalContext.current

    val player =
        remember(
            videoUrl,
        ) {
            ExoPlayer
                .Builder(
                    context,
                )
                .build()
                .apply {
                    setMediaItem(
                        MediaItem.fromUri(
                            videoUrl,
                        ),
                    )

                    repeatMode =
                        Player.REPEAT_MODE_ONE

                    prepare()

                    playWhenReady =
                        true
                }
        }

    var isMuted by
    remember {
        mutableStateOf(
            false,
        )
    }

    var currentPosition by
    remember {
        mutableLongStateOf(
            0L,
        )
    }

    var duration by
    remember {
        mutableLongStateOf(
            0L,
        )
    }

    var isPlaying by
    remember {
        mutableStateOf(
            true,
        )
    }

    DisposableEffect(
        player,
    ) {
        onDispose {
            player.release()
        }
    }

    LaunchedEffect(
        player,
    ) {
        while (isActive) {
            currentPosition =
                player.currentPosition
                    .coerceAtLeast(
                        0L,
                    )

            duration =
                player.duration
                    .takeIf {
                        it > 0L
                    }
                    ?: 0L

            isPlaying =
                player.isPlaying

            delay(PLAYER_PROGRESS_INTERVAL_MS.milliseconds)
        }
    }

    Box(
        modifier =
            modifier
                .background(
                    Color.Black,
                )
                .clickable(
                    interactionSource =
                        remember {
                            MutableInteractionSource()
                        },
                    indication =
                        null,
                ) {
                    if (
                        player.isPlaying
                    ) {
                        player.pause()
                    } else {
                        player.play()
                    }
                },
    ) {
        AndroidView(
            factory = {
                PlayerView(
                    it,
                ).apply {
                    useController =
                        false

                    resizeMode =
                        AspectRatioFrameLayout
                            .RESIZE_MODE_ZOOM

                    layoutParams =
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams
                                .MATCH_PARENT,
                            ViewGroup.LayoutParams
                                .MATCH_PARENT,
                        )

                    this.player =
                        player
                }
            },
            update = {
                it.player =
                    player
            },
            modifier =
                Modifier.fillMaxSize(),
        )

        if (!isPlaying) {
            Image(
                painter =
                    painterResource(
                        MotifIcons.FilledPlay,
                    ),
                contentDescription =
                    null,
                colorFilter =
                    ColorFilter.tint(
                        Color.White,
                    ),
                modifier =
                    Modifier
                        .align(
                            Alignment.Center,
                        )
                        .size(
                            64.dp,
                        ),
            )
        }

        PlayerProgress(
            currentPosition =
                currentPosition,
            duration =
                duration,
            isMuted =
                isMuted,
            onMuteClick = {
                isMuted =
                    !isMuted

                player.volume =
                    if (
                        isMuted
                    ) {
                        0f
                    } else {
                        1f
                    }
            },
            modifier =
                Modifier
                    .align(
                        Alignment.BottomCenter,
                    )
                    .padding(
                        start = 18.dp,
                        end = 18.dp,
                        bottom = 102.dp,
                    ),
        )
    }
}

@Composable
private fun PlayerProgress(
    currentPosition: Long,
    duration: Long,
    isMuted: Boolean,
    onMuteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress =
        if (
            duration > 0L
        ) {
            (
                    currentPosition
                        .toFloat() /
                            duration
                                .toFloat()
                    )
                .coerceIn(
                    0f,
                    1f,
                )
        } else {
            0f
        }

    Column(
        modifier =
            modifier.fillMaxWidth(),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(
                        3.dp,
                    )
                    .background(
                        Color.White.copy(
                            alpha = 0.35f,
                        ),
                    ),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(
                            progress,
                        )
                        .height(
                            3.dp,
                        )
                        .background(
                            Color.White,
                        ),
            )
        }

        Spacer(
            modifier =
                Modifier.height(
                    10.dp,
                ),
        )

        Row(
            modifier =
                Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.SpaceBetween,
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            BasicText(
                text =
                    "${formatTime(currentPosition)} / ${formatTime(duration)}",
                style =
                    TextStyle(
                        color =
                            Color.White,
                        fontSize =
                            12.sp,
                    ),
            )

            Image(
                painter =
                    painterResource(
                        if (
                            isMuted
                        ) {
                            MotifIcons.Mute
                        } else {
                            MotifIcons.Volume
                        },
                    ),
                contentDescription =
                    null,
                colorFilter =
                    ColorFilter.tint(
                        Color.White,
                    ),
                modifier =
                    Modifier
                        .size(
                            28.dp,
                        )
                        .clickable(
                            onClick =
                                onMuteClick,
                        ),
            )
        }
    }
}

@Composable
private fun ResultBottomActions(
    onUploadClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.spacedBy(
                12.dp,
            ),
    ) {
        ResultActionButton(
            iconRes =
                MotifIcons.Upload,
            label =
                stringResource(
                    R.string.capture_result_upload,
                ),
            contentColor =
                Color.Black,
            onClick =
                onUploadClick,
            modifier =
                Modifier.weight(
                    1f,
                ),
        )

        ResultActionButton(
            iconRes =
                MotifIcons.Trash,
            label =
                stringResource(
                    R.string.capture_result_delete,
                ),
            contentColor =
                DeleteColor,
            onClick =
                onDeleteClick,
            modifier =
                Modifier.weight(
                    1f,
                ),
        )
    }
}

@Composable
private fun ResultActionButton(
    iconRes: Int,
    label: String,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .height(
                    58.dp,
                )
                .clip(
                    RoundedCornerShape(
                        16.dp,
                    ),
                )
                .background(
                    Color.White,
                )
                .clickable(
                    onClick =
                        onClick,
                ),
        horizontalArrangement =
            Arrangement.Center,
        verticalAlignment =
            Alignment.CenterVertically,
    ) {
        Image(
            painter =
                painterResource(
                    iconRes,
                ),
            contentDescription =
                null,
            colorFilter =
                ColorFilter.tint(
                    contentColor,
                ),
            modifier =
                Modifier.size(
                    22.dp,
                ),
        )

        Spacer(
            modifier =
                Modifier.width(
                    8.dp,
                ),
        )

        BasicText(
            text =
                label,
            style =
                TextStyle(
                    color =
                        contentColor,
                    fontSize =
                        16.sp,
                    fontWeight =
                        FontWeight.SemiBold,
                ),
        )
    }
}

@Composable
private fun ChallengeUploadOverlay(
    description: String,
    isPublic: Boolean,
    commentEnabled: Boolean,
    onDescriptionChange: (String) -> Unit,
    onVisibilityClick: () -> Unit,
    onCommentEnabledClick: () -> Unit,
    onCancelClick: () -> Unit,
    onConfirmClick: () -> Unit,
) {
    OverlayContainer {
        Column(
            modifier =
                Modifier
                    .width(
                        330.dp,
                    )
                    .clip(
                        RoundedCornerShape(
                            24.dp,
                        ),
                    )
                    .background(
                        Color.White,
                    )
                    .padding(
                        22.dp,
                    ),
        ) {
            BasicTextField(
                value =
                    description,
                onValueChange =
                    onDescriptionChange,
                textStyle =
                    TextStyle(
                        color =
                            Color.Black,
                        fontSize =
                            18.sp,
                        lineHeight =
                            26.sp,
                    ),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(
                            92.dp,
                        ),
                decorationBox = {
                        innerTextField,
                    ->
                    if (
                        description.isBlank()
                    ) {
                        BasicText(
                            text =
                                stringResource(
                                    R.string.capture_result_description_hint,
                                ),
                            style =
                                TextStyle(
                                    color =
                                        Color.Gray,
                                    fontSize =
                                        18.sp,
                                ),
                        )
                    }

                    innerTextField()
                },
            )

            Spacer(
                modifier =
                    Modifier.height(
                        14.dp,
                    ),
            )

            UploadSettingRow(
                label =
                    if (
                        isPublic
                    ) {
                        stringResource(
                            R.string.capture_result_public,
                        )
                    } else {
                        stringResource(
                            R.string.capture_result_private,
                        )
                    },
                iconRes =
                    if (
                        isPublic
                    ) {
                        MotifIcons.Unlock
                    } else {
                        MotifIcons.Lock
                    },
                onClick =
                    onVisibilityClick,
            )

            Spacer(
                modifier =
                    Modifier.height(
                        16.dp,
                    ),
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                verticalAlignment =
                    Alignment.CenterVertically,
                horizontalArrangement =
                    Arrangement.SpaceBetween,
            ) {
                BasicText(
                    text =
                        stringResource(
                            R.string.capture_result_comment_enabled,
                        ),
                    style =
                        TextStyle(
                            color =
                                Color.Black,
                            fontSize =
                                16.sp,
                            fontWeight =
                                FontWeight.Medium,
                        ),
                )

                SimpleToggle(
                    checked =
                        commentEnabled,
                    onClick =
                        onCommentEnabledClick,
                )
            }

            Spacer(
                modifier =
                    Modifier.height(
                        24.dp,
                    ),
            )

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(
                        10.dp,
                    ),
            ) {
                DialogButton(
                    text =
                        stringResource(
                            R.string.capture_result_cancel,
                        ),
                    backgroundColor =
                        Color(
                            0xFFF3F3F3,
                        ),
                    contentColor =
                        Color.Black,
                    onClick =
                        onCancelClick,
                    modifier =
                        Modifier.weight(
                            1f,
                        ),
                )

                DialogButton(
                    text =
                        stringResource(
                            R.string.capture_result_confirm,
                        ),
                    backgroundColor =
                        Color.Black,
                    contentColor =
                        Color.White,
                    onClick =
                        onConfirmClick,
                    modifier =
                        Modifier.weight(
                            1f,
                        ),
                )
            }
        }
    }
}

@Composable
private fun ChallengeDeleteOverlay(
    onCancelClick: () -> Unit,
    onConfirmClick: () -> Unit,
) {
    OverlayContainer {
        Column(
            modifier =
                Modifier
                    .width(
                        330.dp,
                    )
                    .clip(
                        RoundedCornerShape(
                            24.dp,
                        ),
                    )
                    .background(
                        Color.White,
                    )
                    .padding(
                        22.dp,
                    ),
            horizontalAlignment =
                Alignment.CenterHorizontally,
        ) {
            BasicText(
                text =
                    stringResource(
                        R.string.capture_result_delete_title,
                    ),
                style =
                    TextStyle(
                        color =
                            Color.Black,
                        fontSize =
                            20.sp,
                        fontWeight =
                            FontWeight.Bold,
                        textAlign =
                            TextAlign.Center,
                    ),
            )

            Spacer(
                modifier =
                    Modifier.height(
                        12.dp,
                    ),
            )

            BasicText(
                text =
                    stringResource(
                        R.string.capture_result_delete_description,
                    ),
                style =
                    TextStyle(
                        color =
                            Color.Gray,
                        fontSize =
                            15.sp,
                        lineHeight =
                            22.sp,
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

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(
                        10.dp,
                    ),
            ) {
                DialogButton(
                    text =
                        stringResource(
                            R.string.capture_result_cancel,
                        ),
                    backgroundColor =
                        Color(
                            0xFFF3F3F3,
                        ),
                    contentColor =
                        Color.Black,
                    onClick =
                        onCancelClick,
                    modifier =
                        Modifier.weight(
                            1f,
                        ),
                )

                DialogButton(
                    text =
                        stringResource(
                            R.string.capture_result_confirm,
                        ),
                    backgroundColor =
                        Color.Black,
                    contentColor =
                        Color.White,
                    onClick =
                        onConfirmClick,
                    modifier =
                        Modifier.weight(
                            1f,
                        ),
                )
            }
        }
    }
}

@Composable
private fun OverlayContainer(
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Color.Black.copy(
                        alpha = 0.55f,
                    ),
                ),
        contentAlignment =
            Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun UploadSettingRow(
    label: String,
    iconRes: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(
                    onClick =
                        onClick,
                )
                .padding(
                    vertical = 4.dp,
                ),
        verticalAlignment =
            Alignment.CenterVertically,
        horizontalArrangement =
            Arrangement.SpaceBetween,
    ) {
        BasicText(
            text =
                label,
            style =
                TextStyle(
                    color =
                        Color.Black,
                    fontSize =
                        16.sp,
                    fontWeight =
                        FontWeight.Medium,
                ),
        )

        Image(
            painter =
                painterResource(
                    iconRes,
                ),
            contentDescription =
                null,
            colorFilter =
                ColorFilter.tint(
                    Color.Black,
                ),
            modifier =
                Modifier.size(
                    24.dp,
                ),
        )
    }
}

@Composable
private fun SimpleToggle(
    checked: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .width(
                    44.dp,
                )
                .height(
                    24.dp,
                )
                .clip(
                    RoundedCornerShape(
                        12.dp,
                    ),
                )
                .background(
                    if (
                        checked
                    ) {
                        ToggleEnabledColor
                    } else {
                        Color(
                            0xFFD4D4D4,
                        )
                    },
                )
                .clickable(
                    onClick =
                        onClick,
                ),
    ) {
        Box(
            modifier =
                Modifier
                    .padding(
                        2.dp,
                    )
                    .align(
                        if (
                            checked
                        ) {
                            Alignment.CenterEnd
                        } else {
                            Alignment.CenterStart
                        },
                    )
                    .size(
                        20.dp,
                    )
                    .clip(
                        CircleShape,
                    )
                    .background(
                        Color.White,
                    ),
        )
    }
}

@Composable
private fun DialogButton(
    text: String,
    backgroundColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .height(
                    48.dp,
                )
                .clip(
                    RoundedCornerShape(
                        12.dp,
                    ),
                )
                .background(
                    backgroundColor,
                )
                .clickable(
                    onClick =
                        onClick,
                ),
        contentAlignment =
            Alignment.Center,
    ) {
        BasicText(
            text =
                text,
            style =
                TextStyle(
                    color =
                        contentColor,
                    fontSize =
                        16.sp,
                    fontWeight =
                        FontWeight.SemiBold,
                ),
        )
    }
}

@Composable
private fun CompositeProcessingContent(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier,
        contentAlignment =
            Alignment.Center,
    ) {
        Column(
            horizontalAlignment =
                Alignment.CenterHorizontally,
        ) {
            LoadingWheel()

            Spacer(
                modifier =
                    Modifier.height(
                        20.dp,
                    ),
            )

            BasicText(
                text =
                    stringResource(
                        R.string.capture_result_processing,
                    ),
                style =
                    ResultMessageStyle,
            )
        }
    }
}

@Composable
private fun CompositeFailedContent(
    failCode: String?,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier,
        contentAlignment =
            Alignment.Center,
    ) {
        Column(
            horizontalAlignment =
                Alignment.CenterHorizontally,
        ) {
            BasicText(
                text =
                    stringResource(
                        R.string.capture_result_failed,
                    ),
                style =
                    ResultMessageStyle,
            )

            if (!failCode.isNullOrBlank()) {
                Spacer(
                    modifier =
                        Modifier.height(
                            8.dp,
                        ),
                )

                BasicText(
                    text =
                        failCode,
                    style =
                        TextStyle(
                            color =
                                Color.White.copy(
                                    alpha = 0.65f,
                                ),
                            fontSize =
                                14.sp,
                        ),
                )
            }

            Spacer(
                modifier =
                    Modifier.height(
                        24.dp,
                    ),
            )

            DialogButton(
                text =
                    stringResource(
                        R.string.capture_result_retry,
                    ),
                backgroundColor =
                    Color.White,
                contentColor =
                    Color.Black,
                onClick =
                    onRetryClick,
                modifier =
                    Modifier.width(
                        160.dp,
                    ),
            )
        }
    }
}

@Composable
private fun CompositeLoadFailedContent(
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier,
        contentAlignment =
            Alignment.Center,
    ) {
        Column(
            horizontalAlignment =
                Alignment.CenterHorizontally,
        ) {
            BasicText(
                text =
                    stringResource(
                        R.string.capture_result_load_failed,
                    ),
                style =
                    ResultMessageStyle,
            )

            Spacer(
                modifier =
                    Modifier.height(
                        24.dp,
                    ),
            )

            DialogButton(
                text =
                    stringResource(
                        R.string.capture_result_retry,
                    ),
                backgroundColor =
                    Color.White,
                contentColor =
                    Color.Black,
                onClick =
                    onRetryClick,
                modifier =
                    Modifier.width(
                        160.dp,
                    ),
            )
        }
    }
}

@Composable
private fun ResultBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(
                    48.dp,
                )
                .clickable(
                    onClick =
                        onClick,
                ),
        contentAlignment =
            Alignment.Center,
    ) {
        Image(
            painter =
                painterResource(
                    MotifIcons.ArrowLeft,
                ),
            contentDescription =
                stringResource(
                    R.string.capture_back,
                ),
            colorFilter =
                ColorFilter.tint(
                    Color.White,
                ),
            modifier =
                Modifier.size(
                    30.dp,
                ),
        )
    }
}

private fun formatTime(
    milliseconds: Long,
): String {
    val totalSeconds =
        milliseconds
            .coerceAtLeast(
                0L,
            ) / 1_000L

    val minutes =
        totalSeconds / 60L

    val seconds =
        totalSeconds % 60L

    return "%d:%02d".format(
        minutes,
        seconds,
    )
}

private val ResultMessageStyle =
    TextStyle(
        color =
            Color.White,
        fontSize =
            20.sp,
        fontWeight =
            FontWeight.SemiBold,
        textAlign =
            TextAlign.Center,
    )

private val DeleteColor =
    Color(
        0xFFFF3B7A,
    )

private val ToggleEnabledColor =
    Color(
        0xFFFF3B9D,
    )

private const val PLAYER_PROGRESS_INTERVAL_MS =
    250L