package com.ssafy.motif.feature.challenges.impl.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ssafy.motif.core.designsystem.component.ClickableSurface
import com.ssafy.motif.core.designsystem.component.Text
import com.ssafy.motif.core.designsystem.glass.GlassBackdropState
import com.ssafy.motif.core.designsystem.icon.MotifIcons
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.core.model.challenges.ChallengeShortForm
import com.ssafy.motif.core.ui.video.VideoSongInfo

@Composable
internal fun ArtistChallengeOverlay(
    challenge: ChallengeShortForm,
    backdropState: GlassBackdropState,
    onArtistClick: () -> Unit,
    onSubscribeClick: (Long) -> Unit,
    onDescriptionClick: () -> Unit,
    onLikeClick: () -> Unit,
    onCommentClick: () -> Unit,
    onCaptureClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ShortFormOverlay(
        modifier = modifier,
        actions = {
            ChallengeShortFormActions(
                challenge = challenge,
                onLikeClick = onLikeClick,
                onCommentClick = onCommentClick,
                onCaptureClick = onCaptureClick,
            )
        },
        information = {
            ChallengeShortFormInformation(
                challenge = challenge,
                backdropState = backdropState,
                onArtistClick = onArtistClick,
                onSubscribeClick = onSubscribeClick,
                onDescriptionClick = onDescriptionClick,
            )
        },
    )
}

@Composable
private fun ChallengeShortFormInformation(
    challenge: ChallengeShortForm,
    backdropState: GlassBackdropState,
    onArtistClick: () -> Unit,
    onSubscribeClick: (Long) -> Unit,
    onDescriptionClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = challenge.artist.profileImageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(
                        onClick = onArtistClick,
                    ),
            )

            Spacer(
                modifier = Modifier.width(8.dp),
            )

            Text(
                text = "@${challenge.artist.name}",
                color = MotifTheme.colors.white,
                style = MotifTheme.typography.bodySB16,
                modifier = Modifier.clickable(
                    onClick = onArtistClick,
                ),
            )

            Spacer(
                modifier = Modifier.width(12.dp),
            )

            ChallengeSubscribeButton(
                isSubscribed = challenge.isSubscribed,
                onClick = { onSubscribeClick(challenge.artist.id) },
            )
        }

        Text(
            text = challenge.description,
            color = MotifTheme.colors.white,
            style = MotifTheme.typography.bodyR14,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(vertical = 8.dp)
                .clickable(
                    interactionSource = remember {
                        MutableInteractionSource()
                    },
                    indication = null,
                    onClick = onDescriptionClick,
                )
                .padding(vertical = 4.dp),
        )

        VideoSongInfo(
            artistName = challenge.songArtistName,
            songTitle = challenge.songTitle,
            backdropState = backdropState,
        )
    }
}

@Composable
private fun ChallengeSubscribeButton(
    isSubscribed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ClickableSurface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = if (isSubscribed) {
            MotifTheme.colors.pink
        } else {
            MotifTheme.colors.white
        },
        contentColor = if (isSubscribed) {
            MotifTheme.colors.white
        } else {
            MotifTheme.colors.black
        },
    ) {
        Text(
            text = "WITH",
            style = MotifTheme.typography.bodySB12,
            modifier = Modifier.padding(
                horizontal = 8.dp,
                vertical = 4.dp,
            ),
        )
    }
}

@Composable
private fun ChallengeShortFormActions(
    challenge: ChallengeShortForm,
    onLikeClick: () -> Unit,
    onCommentClick: () -> Unit,
    onCaptureClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        ShortFormAction(
            iconRes = if (challenge.isLiked) {
                MotifIcons.FilledHeart
            } else {
                MotifIcons.Heart
            },
            count = challenge.likeCount,
            contentDescription = "좋아요",
            tint = if (challenge.isLiked) {
                MotifTheme.colors.pink
            } else {
                MotifTheme.colors.white
            },
            onClick = onLikeClick,
        )

        ShortFormAction(
            iconRes = MotifIcons.Message,
            count = challenge.commentCount,
            contentDescription = "댓글",
            onClick = onCommentClick,
        )

        ShortFormAction(
            iconRes = MotifIcons.Camera,
            count = challenge.participantCount,
            contentDescription = "챌린지 촬영",
            onClick = onCaptureClick,
        )
    }
}