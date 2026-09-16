package com.ssafy.motif.feature.challenges.impl.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ssafy.motif.core.designsystem.component.ClickableSurface
import com.ssafy.motif.core.designsystem.component.GlassSurface
import com.ssafy.motif.core.designsystem.component.Icon
import com.ssafy.motif.core.designsystem.component.Text
import com.ssafy.motif.core.designsystem.glass.GlassBackdropState
import com.ssafy.motif.core.designsystem.icon.MotifIcons
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.core.model.challenges.ChallengeShortForm

@Composable
internal fun ChallengeShortFormOverlay(
    challenge: ChallengeShortForm,
    backdropState: GlassBackdropState,
    onArtistClick: () -> Unit,
    onSubscribeClick: () -> Unit,
    onDescriptionClick: () -> Unit,
    onLikeClick: () -> Unit,
    onCommentClick: () -> Unit,
    onCaptureClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        ChallengeActionColumn(
            challenge = challenge,
            onLikeClick = onLikeClick,
            onCommentClick = onCommentClick,
            onCaptureClick = onCaptureClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 64.dp),
        )

        ChallengeInformation(
            challenge = challenge,
            backdropState = backdropState,
            onArtistClick = onArtistClick,
            onSubscribeClick = onSubscribeClick,
            onDescriptionClick = onDescriptionClick,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(end = 80.dp),
        )
    }
}

@Composable
private fun ChallengeActionColumn(
    challenge: ChallengeShortForm,
    onLikeClick: () -> Unit,
    onCommentClick: () -> Unit,
    onCaptureClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment =
            Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.spacedBy(24.dp),
    ) {
        ChallengeAction(
            icon =
                if (challenge.isLiked) {
                    MotifIcons.FilledHeart
                } else {
                    MotifIcons.Heart
                },
            count = challenge.likeCount,
            contentDescription = "좋아요",
            tint =
                if (challenge.isLiked) {
                    MotifTheme.colors.pink
                } else {
                    Color.White
                },
            onClick = onLikeClick,
        )

        ChallengeAction(
            icon = MotifIcons.Message,
            count = challenge.commentCount,
            contentDescription = "댓글",
            onClick = onCommentClick,
        )

        ChallengeAction(
            icon = MotifIcons.Camera,
            count = challenge.participantCount,
            contentDescription = "챌린지 촬영",
            onClick = onCaptureClick,
        )
    }
}

@Composable
private fun ChallengeAction(
    icon: Int,
    count: Long,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
) {
    Column(
        modifier = modifier
            .clickable(
                interactionSource =
                    remember {
                        MutableInteractionSource()
                    },
                indication = null,
                onClick = onClick,
            ),
        horizontalAlignment =
            Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription =
                contentDescription,
            tint = tint,
            modifier = Modifier.size(28.dp),
        )

        Spacer(
            modifier = Modifier.size(4.dp),
        )

        Text(
            text = formatShortCount(count),
            color = Color.White,
            style = MotifTheme.typography.bodyR16,
        )
    }
}

@Composable
private fun ChallengeInformation(
    challenge: ChallengeShortForm,
    backdropState: GlassBackdropState,
    onArtistClick: () -> Unit,
    onSubscribeClick: () -> Unit,
    onDescriptionClick: () -> Unit,
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
                    challenge.artist.profileImageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(
                        onClick = onArtistClick,
                    ),
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "@${challenge.artist.name}",
                color = Color.White,
                style =
                    MotifTheme.typography.bodySB16,
                modifier =
                    Modifier.clickable(
                        onClick = onArtistClick,
                    ),
            )

            Spacer(modifier = Modifier.width(12.dp))

            SubscribeButton(
                isSubscribed = challenge.isSubscribed,
                onClick = onSubscribeClick,
            )
        }

        Text(
            text = challenge.description,
            color = Color.White,
            style = MotifTheme.typography.bodyR14,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(vertical = 8.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDescriptionClick,
                )
                .padding(vertical = 4.dp),
        )

        ChallengeSongInfo(
            artistName = challenge.songArtistName,
            songTitle = challenge.songTitle,
            backdropState = backdropState,
        )
    }
}

@Composable
private fun SubscribeButton(
    isSubscribed: Boolean,
    onClick: () -> Unit,
) {
    ClickableSurface(
        onClick = onClick,
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
private fun ChallengeSongInfo(
    artistName: String,
    songTitle: String,
    backdropState: GlassBackdropState,
    modifier: Modifier = Modifier,
) {
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(
                horizontal = 12.dp,
                vertical = 6.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(MotifIcons.FilledPlay),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "$artistName · $songTitle",
                color = Color.White,
                style = MotifTheme.typography.bodyR16,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    GlassSurface(
        backdropState = backdropState,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        content = content,
    )
}

private fun formatShortCount(
    count: Long,
): String =
    when {
        count >= 10_000 -> {
            val value =
                count / 10_000f

            if (value % 1f == 0f) {
                "${value.toInt()}만"
            } else {
                "%.1f만".format(value)
            }
        }

        count >= 1_000 -> {
            val value =
                count / 1_000f

            if (value % 1f == 0f) {
                "${value.toInt()}천"
            } else {
                "%.1f천".format(value)
            }
        }

        else -> count.toString()
    }