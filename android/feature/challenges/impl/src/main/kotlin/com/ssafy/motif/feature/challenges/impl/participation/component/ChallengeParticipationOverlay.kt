package com.ssafy.motif.feature.challenges.impl.participation.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import coil.compose.AsyncImage
import com.ssafy.motif.core.designsystem.component.ClickableSurface
import com.ssafy.motif.core.designsystem.component.IconButton
import com.ssafy.motif.core.designsystem.component.ModifIconButtonDefaults
import com.ssafy.motif.core.designsystem.component.Surface
import com.ssafy.motif.core.designsystem.component.Text
import com.ssafy.motif.core.designsystem.icon.MotifIcons
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.core.model.challenge.ChallengeParticipation
import com.ssafy.motif.core.ui.formatter.formatCount
import com.ssafy.motif.feature.challenges.impl.R
import com.ssafy.motif.feature.challenges.impl.component.ShortFormAction
import com.ssafy.motif.feature.challenges.impl.component.ShortFormOverlay

@Composable
internal fun ChallengeParticipationOverlay(
    participation: ChallengeParticipation,
    onBackClick: () -> Unit,
    onAuthorClick: () -> Unit,
    onLikeClick: () -> Unit,
    onCommentClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
    onEditClick: (() -> Unit)? = null,
) {
    ShortFormOverlay(
        modifier = modifier,
        topBar = {
            ParticipationTopBar(
                isMine =
                    participation.isMine,
                onBackClick =
                    onBackClick,
                onEditClick =
                    onEditClick,
                onDeleteClick =
                    onDeleteClick,
            )
        },
        actions = {
            ParticipationActions(
                participation =
                    participation,
                onLikeClick =
                    onLikeClick,
                onCommentClick =
                    onCommentClick,
            )
        },
        information = {
            ParticipationInformation(
                participation =
                    participation,
                onAuthorClick =
                    onAuthorClick,
            )
        },
    )
}

@Composable
private fun ParticipationTopBar(
    isMine: Boolean,
    onBackClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
    onEditClick: (() -> Unit)? = null,
) {
    Row(
        modifier =
            modifier.fillMaxWidth(),
        verticalAlignment =
            Alignment.CenterVertically,
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
        )

        Spacer(
            modifier =
                Modifier.weight(1f),
        )

        if (isMine) {
            ParticipationMoreMenu(
                onEditClick =
                    onEditClick,
                onDeleteClick =
                    onDeleteClick,
            )
        }
    }
}

@Composable
private fun ParticipationMoreMenu(
    onDeleteClick: () -> Unit,
    onEditClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var expanded by
    remember {
        mutableStateOf(false)
    }

    val density =
        LocalDensity.current

    val popupOffset =
        with(density) {
            IntOffset(
                x = 0,
                y = MORE_BUTTON_SIZE.roundToPx(),
            )
        }

    Box(
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .size(
                    MORE_BUTTON_SIZE,
                )
                .clickable(
                    interactionSource =
                        remember {
                            MutableInteractionSource()
                        },
                    indication = null,
                    onClick = {
                        expanded = true
                    },
                ),
            contentAlignment =
                Alignment.Center,
        ) {
            Text(
                text = "⋮",
                color =
                    MotifTheme.colors.white,
                style =
                    MotifTheme.typography.titleSB20,
            )
        }

        if (expanded) {
            Popup(
                alignment =
                    Alignment.TopEnd,
                offset =
                    popupOffset,
                onDismissRequest = {
                    expanded = false
                },
                properties =
                    PopupProperties(
                        focusable = true,
                        dismissOnBackPress = true,
                        dismissOnClickOutside = true,
                    ),
            ) {
                ParticipationMoreMenuContent(
                    onEditClick =
                        onEditClick?.let { editClick ->
                            {
                                expanded = false
                                editClick()
                            }
                        },
                    onDeleteClick = {
                        expanded = false
                        onDeleteClick()
                    },
                )
            }
        }
    }
}

@Composable
private fun ParticipationMoreMenuContent(
    onDeleteClick: () -> Unit,
    onEditClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier.width(
                MORE_MENU_WIDTH,
            ),
        shape =
            RoundedCornerShape(
                MORE_MENU_CORNER_RADIUS,
            ),
        color =
            MotifTheme.colors.background,
        shadowElevation =
            MORE_MENU_ELEVATION,
    ) {
        Column {
            if (onEditClick != null) {
                ParticipationMoreMenuItem(
                    text =
                        stringResource(
                            R.string.challenge_participation_edit,
                        ),
                    onClick =
                        onEditClick,
                )
            }

            ParticipationMoreMenuItem(
                text =
                    stringResource(
                        R.string.challenge_participation_delete,
                    ),
                onClick =
                    onDeleteClick,
                contentColor =
                    MotifTheme.colors.pink,
            )
        }
    }
}

@Composable
private fun ParticipationMoreMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentColor: androidx.compose.ui.graphics.Color =
        MotifTheme.colors.typo,
) {
    ClickableSurface(
        onClick =
            onClick,
        modifier =
            modifier.fillMaxWidth(),
        color =
            MotifTheme.colors.background,
        contentColor =
            contentColor,
        interactionSource =
            remember {
                MutableInteractionSource()
            },
        indication = null,
        contentPadding =
            PaddingValues(
                horizontal =
                    MORE_MENU_HORIZONTAL_PADDING,
                vertical =
                    MORE_MENU_VERTICAL_PADDING,
            ),
    ) {
        Text(
            text =
                text,
            color =
                contentColor,
            style =
                MotifTheme.typography.bodyR14,
        )
    }
}

@Composable
private fun ParticipationInformation(
    participation: ChallengeParticipation,
    onAuthorClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
    ) {
        Row(
            verticalAlignment =
                Alignment.CenterVertically,
        ) {
            AsyncImage(
                model =
                    participation
                        .author
                        .profileImageUrl,
                contentDescription =
                    null,
                contentScale =
                    ContentScale.Crop,
                modifier = Modifier
                    .size(40.dp)
                    .clip(
                        CircleShape,
                    )
                    .clickable(
                        interactionSource =
                            remember {
                                MutableInteractionSource()
                            },
                        indication =
                            null,
                        onClick =
                            onAuthorClick,
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
                    "@${participation.author.nickname}",
                color =
                    MotifTheme.colors.white,
                style =
                    MotifTheme.typography.bodySB16,
                modifier =
                    Modifier.clickable(
                        interactionSource =
                            remember {
                                MutableInteractionSource()
                            },
                        indication =
                            null,
                        onClick =
                            onAuthorClick,
                    ),
            )
        }

        if (
            participation
                .description
                .isNotBlank()
        ) {
            Text(
                text =
                    participation.description,
                color =
                    MotifTheme.colors.white,
                style =
                    MotifTheme.typography.bodyR14,
                maxLines =
                    2,
                overflow =
                    TextOverflow.Ellipsis,
                modifier =
                    Modifier.padding(
                        top = 8.dp,
                    ),
            )
        }

        Text(
            text =
                stringResource(
                    R.string.challenge_participation_view_count,
                    participation
                        .viewCount
                        .formatCount(),
                ),
            color =
                MotifTheme.colors.gray300,
            style =
                MotifTheme.typography.bodyR12,
            modifier =
                Modifier.padding(
                    top = 6.dp,
                ),
        )
    }
}

@Composable
private fun ParticipationActions(
    participation: ChallengeParticipation,
    onLikeClick: () -> Unit,
    onCommentClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment =
            Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.spacedBy(
                24.dp,
            ),
    ) {
        ShortFormAction(
            iconRes =
                if (
                    participation.isLiked
                ) {
                    MotifIcons.FilledHeart
                } else {
                    MotifIcons.Heart
                },
            count =
                participation.likeCount,
            contentDescription =
                stringResource(
                    R.string.challenge_participation_like,
                ),
            tint =
                if (
                    participation.isLiked
                ) {
                    MotifTheme.colors.pink
                } else {
                    MotifTheme.colors.white
                },
            onClick =
                onLikeClick,
        )

        ShortFormAction(
            iconRes =
                MotifIcons.Message,
            count =
                participation.commentCount,
            contentDescription =
                stringResource(
                    R.string.challenge_participation_comment,
                ),
            tint =
                if (
                    participation.commentEnabled
                ) {
                    MotifTheme.colors.white
                } else {
                    MotifTheme.colors.gray500
                },
            onClick = {
                if (
                    participation.commentEnabled
                ) {
                    onCommentClick()
                }
            },
        )
    }
}

private val MORE_BUTTON_SIZE =
    40.dp

private val MORE_MENU_WIDTH =
    120.dp

private val MORE_MENU_CORNER_RADIUS =
    12.dp

private val MORE_MENU_ELEVATION =
    8.dp

private val MORE_MENU_HORIZONTAL_PADDING =
    16.dp

private val MORE_MENU_VERTICAL_PADDING =
    12.dp