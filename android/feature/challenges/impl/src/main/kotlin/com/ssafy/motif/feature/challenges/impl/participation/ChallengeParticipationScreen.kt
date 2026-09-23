package com.ssafy.motif.feature.challenges.impl.participation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.motif.core.designsystem.component.IconButton
import com.ssafy.motif.core.designsystem.component.LoadingWheel
import com.ssafy.motif.core.designsystem.component.ModifIconButtonDefaults
import com.ssafy.motif.core.designsystem.component.Text
import com.ssafy.motif.core.designsystem.component.bottomsheet.rememberBottomSheetState
import com.ssafy.motif.core.designsystem.icon.MotifIcons
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.core.model.challenge.ChallengeParticipation
import com.ssafy.motif.core.ui.comment.CommentBottomSheet
import com.ssafy.motif.core.ui.comment.CommentUiState
import com.ssafy.motif.feature.challenges.impl.R
import com.ssafy.motif.feature.challenges.impl.component.ChallengeShortFormLayout
import com.ssafy.motif.feature.challenges.impl.component.KeyboardBackgroundScrim
import com.ssafy.motif.feature.challenges.impl.participation.component.ChallengeParticipationOverlay

@Composable
internal fun ChallengeParticipationScreen(
    challengeId: Long?,
    initialVideoId: Long?,
    myProfileImageUrl: String,
    onBackClick: () -> Unit,
    onHomeClick: () -> Unit,
    onMyClick: () -> Unit,
    onMotifClick: () -> Unit,
    modifier: Modifier = Modifier,
    onAuthorClick: (Long) -> Unit = {},
    onEditClick: ((Long) -> Unit)? = null,
    viewModel: ChallengeParticipationViewModel =
        hiltViewModel(),
) {
    val uiState by
    viewModel.uiState
        .collectAsStateWithLifecycle()

    val commentUiState by
    viewModel.commentUiState
        .collectAsStateWithLifecycle()

    LaunchedEffect(
        challengeId,
        initialVideoId,
    ) {
        viewModel.initialize(
            challengeId =
                challengeId,
            initialVideoId =
                initialVideoId,
        )
    }

    when (
        val state =
            uiState
    ) {
        ChallengeParticipationUiState.Loading -> {
            ParticipationLoadingScreen(
                onBackClick =
                    onBackClick,
                modifier =
                    modifier,
            )
        }

        is ChallengeParticipationUiState.Success -> {
            ChallengeParticipationScreen(
                participations =
                    state.participations,
                initialPage =
                    state.initialPage,
                myProfileImageUrl =
                    myProfileImageUrl,
                commentUiState =
                    commentUiState,
                onBackClick =
                    onBackClick,
                onAuthorClick =
                    onAuthorClick,
                onLikeClick =
                    viewModel::toggleParticipationLike,
                onCommentClick =
                    viewModel::loadVideoComments,
                onCommentLikeClick =
                    viewModel::toggleCommentLike,
                onReplyClick =
                    viewModel::loadReplies,
                onCommentBackClick =
                    viewModel::backToComments,
                onCommentDismiss =
                    viewModel::dismissComments,
                onCommentSendClick =
                    viewModel::sendComment,
                onDeleteClick =
                    viewModel::deleteParticipation,
                onEditClick =
                    onEditClick,
                onHomeClick =
                    onHomeClick,
                onMyClick =
                    onMyClick,
                onMotifClick =
                    onMotifClick,
                onLoadMore =
                    viewModel::loadMoreParticipations,
                modifier =
                    modifier,
            )
        }

        is ChallengeParticipationUiState.Empty -> {
            ParticipationEmptyScreen(
                onBackClick =
                    onBackClick,
                modifier =
                    modifier,
            )
        }

        is ChallengeParticipationUiState.Error -> {
            ParticipationErrorScreen(
                onBackClick =
                    onBackClick,
                onRetry =
                    viewModel::retry,
                modifier =
                    modifier,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChallengeParticipationScreen(
    participations:
    List<ChallengeParticipation>,
    initialPage: Int,
    myProfileImageUrl: String,
    commentUiState: CommentUiState,
    onBackClick: () -> Unit,
    onAuthorClick: (Long) -> Unit,
    onLikeClick: (Long) -> Unit,
    onCommentClick: (Long) -> Unit,
    onCommentLikeClick: (Long) -> Unit,
    onReplyClick: (Long) -> Unit,
    onCommentBackClick: () -> Unit,
    onCommentDismiss: () -> Unit,
    onCommentSendClick: (String) -> Unit,
    onDeleteClick: (Long) -> Unit,
    onEditClick: ((Long) -> Unit)?,
    onHomeClick: () -> Unit,
    onMyClick: () -> Unit,
    onMotifClick: () -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager =
        LocalFocusManager.current

    val commentSheetState =
        rememberBottomSheetState(
            skipPartiallyExpanded =
                false,
            partiallyExpandedFraction =
                COMMENT_SHEET_FRACTION,
        )

    var commentVideoId by
    remember {
        mutableStateOf<Long?>(
            null,
        )
    }

    val commentVideo =
        commentVideoId
            ?.let { videoId ->
                participations
                    .firstOrNull {
                            participation ->
                        participation.id ==
                                videoId
                    }
            }

    val activeSheetState =
        if (commentVideo != null) {
            commentSheetState
        } else {
            null
        }

    val isCommentKeyboardVisible =
        commentVideo != null &&
                WindowInsets.isImeVisible

    LaunchedEffect(
        participations,
        commentVideoId,
    ) {
        if (
            commentVideoId != null &&
            commentVideo == null
        ) {
            commentVideoId = null
            onCommentDismiss()
        }
    }

    Box(
        modifier =
            modifier.fillMaxSize(),
    ) {
        ChallengeShortFormLayout(
            itemCount =
                participations.size,
            videoId = { index ->
                participations[index]
                    .id
                    .toString()
            },
            videoUrl = { index ->
                participations[index]
                    .videoUrl
            },
            thumbnailUrl = { index ->
                participations[index]
                    .thumbnailUrl
            },
            initialPage =
                initialPage,
            myProfileImageUrl =
                myProfileImageUrl,
            activeSheetState =
                activeSheetState,
            onHomeClick =
                onHomeClick,
            onMyClick =
                onMyClick,
            onMotifClick =
                onMotifClick,
            onLoadMore =
                onLoadMore,
            modifier =
                Modifier.fillMaxSize(),
        ) { index, _ ->
            val participation =
                participations[index]

            ChallengeParticipationOverlay(
                participation =
                    participation,
                onBackClick =
                    onBackClick,
                onAuthorClick = {
                    onAuthorClick(
                        participation
                            .author
                            .id,
                    )
                },
                onLikeClick = {
                    onLikeClick(
                        participation.id,
                    )
                },
                onCommentClick = {
                    if (participation.commentEnabled) {
                        commentVideoId = participation.id

                        onCommentClick(
                            participation.id,
                        )
                    }
                },
                onDeleteClick = {
                    onDeleteClick(
                        participation.id,
                    )
                },
                onEditClick =
                    onEditClick
                        ?.let { editClick ->
                            {
                                editClick(
                                    participation.id,
                                )
                            }
                        },
            )
        }

        if (
            isCommentKeyboardVisible
        ) {
            KeyboardBackgroundScrim(
                onClick =
                    focusManager::clearFocus,
            )
        }

        commentVideo
            ?.let { participation ->
                CommentBottomSheet(
                    commentCount =
                        participation
                            .commentCount,
                    state =
                        commentUiState,
                    sheetState =
                        commentSheetState,
                    onDismissRequest = {
                        commentVideoId =
                            null

                        onCommentDismiss()
                    },
                    onLikeClick =
                        onCommentLikeClick,
                    onReplyClick =
                        onReplyClick,
                    onBackClick =
                        onCommentBackClick,
                    onCommentSendClick =
                        onCommentSendClick,
                )
            }
    }
}

@Composable
private fun ParticipationLoadingScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ParticipationStateContainer(
        onBackClick =
            onBackClick,
        modifier =
            modifier,
    ) {
        LoadingWheel()
    }
}

@Composable
private fun ParticipationEmptyScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ParticipationStateContainer(
        onBackClick =
            onBackClick,
        modifier =
            modifier,
    ) {
        Text(
            text =
                stringResource(
                    R.string.challenge_participation_empty,
                ),
            color =
                MotifTheme.colors.white,
            style =
                MotifTheme.typography
                    .bodyR16,
        )
    }
}

@Composable
private fun ParticipationErrorScreen(
    onBackClick: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ParticipationStateContainer(
        onBackClick =
            onBackClick,
        modifier =
            modifier,
    ) {
        Column(
            horizontalAlignment =
                Alignment.CenterHorizontally,
            verticalArrangement =
                Arrangement.spacedBy(
                    12.dp,
                ),
        ) {
            Text(
                text =
                    stringResource(
                        R.string.challenge_participation_error,
                    ),
                color =
                    MotifTheme.colors.white,
                style =
                    MotifTheme.typography
                        .bodyR14,
            )

            Text(
                text =
                    stringResource(
                        R.string.challenge_participation_retry,
                    ),
                color =
                    MotifTheme.colors.white,
                style =
                    MotifTheme.typography
                        .bodySB14,
                modifier =
                    Modifier.clickable(
                        onClick =
                            onRetry,
                    ),
            )
        }
    }
}

@Composable
private fun ParticipationStateContainer(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Color.Black,
            ),
    ) {
        IconButton(
            painter =
                painterResource(
                    MotifIcons.BarArrowLeft,
                ),
            contentDescription =
                stringResource(
                    R.string.challenge_participation_back,
                ),
            size = 24.dp,
            onClick =
                onBackClick,
            colors =
                ModifIconButtonDefaults
                    .iconButtonColors(
                        contentColor =
                            MotifTheme.colors.white,
                    ),
            modifier =
                Modifier.align(
                    Alignment.TopStart,
                ),
        )

        Box(
            modifier =
                Modifier.align(
                    Alignment.Center,
                ),
        ) {
            content()
        }
    }
}

private const val COMMENT_SHEET_FRACTION =
    0.6f