package com.ssafy.motif.core.ui.comment

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ssafy.motif.core.designsystem.component.Icon
import com.ssafy.motif.core.designsystem.component.Text
import com.ssafy.motif.core.designsystem.icon.MotifIcons
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.core.model.comment.Comment
import com.ssafy.motif.core.model.comment.CommentAuthorType
import com.ssafy.motif.core.ui.R
import com.ssafy.motif.core.ui.formatter.formatCount
import com.ssafy.motif.core.ui.formatter.formatCreatedAt
import com.ssafy.motif.core.designsystem.R as DesignSystemR

@Composable
internal fun CommentItem(
    comment: Comment,
    onLikeClick: () -> Unit,
    modifier: Modifier = Modifier,
    onReplyClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        AsyncImage(
            model = comment.profileImageUrl,
            placeholder = painterResource(DesignSystemR.drawable.img_default_profile),
            error = painterResource(DesignSystemR.drawable.img_default_profile),
            fallback = painterResource(DesignSystemR.drawable.img_default_profile),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape),
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            CommentAuthor(
                nickname = comment.nickname,
                createdAt = comment.createdAt.formatCreatedAt(),
                authorType = comment.authorType,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = comment.content,
                color = MotifTheme.colors.typo,
                style = MotifTheme.typography.bodyR16,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                CommentAction(
                    iconRes = if (comment.isLiked) {
                        DesignSystemR.drawable.ic_filled_heart_24
                    } else {
                        DesignSystemR.drawable.ic_heart_24
                    },
                    count = comment.likeCount,
                    tint = if (comment.isLiked) MotifTheme.colors.pink else MotifTheme.colors.typo,
                    onClick = onLikeClick,
                )

                if (onReplyClick != null) {
                    Spacer(modifier = Modifier.width(28.dp))

                    CommentAction(
                        iconRes = DesignSystemR.drawable.ic_message_24,
                        count = comment.replyCount,
                        tint = MotifTheme.colors.typo,
                        onClick = onReplyClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun CommentAction(
    @DrawableRes iconRes: Int,
    count: Long,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = tint,
        )

        Text(
            text = count.formatCount(),
            color = MotifTheme.colors.typo,
            style = MotifTheme.typography.bodyR14,
        )
    }
}

@Composable
private fun CommentAuthor(
    nickname: String,
    createdAt: String,
    modifier: Modifier = Modifier,
    authorType: CommentAuthorType = CommentAuthorType.USER,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = nickname,
            color = MotifTheme.colors.typo,
            style = MotifTheme.typography.bodySB16,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (authorType == CommentAuthorType.ARTIST) {
            Spacer(modifier = Modifier.width(4.dp))

            Icon(
                painter = painterResource(MotifIcons.ArtistCheck),
                contentDescription = stringResource(R.string.comment_artist_badge),
                modifier = Modifier.size(16.dp),
                tint = Color.Unspecified,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = createdAt,
            color = MotifTheme.colors.gray500,
            style = MotifTheme.typography.bodyR14,
        )
    }
}

@Preview(
    name = "Comment",
    showBackground = true,
)
@Composable
private fun CommentItemPreview() {
    val context = LocalContext.current
    val profileImageUrl =
        "android.resource://${context.packageName}/${DesignSystemR.drawable.img_profile}"

    MotifTheme {
        Box(
            modifier = Modifier
                .background(MotifTheme.colors.background)
                .padding(20.dp),
        ) {
            CommentItem(
                comment = Comment(
                    id = 1L,
                    profileImageUrl = profileImageUrl,
                    nickname = "dance_with_me",
                    content = "이 부분 안무 진짜 중독성 있다 ㅋㅋ",
                    createdAt = System.currentTimeMillis() - 5 * 60_000L,
                    likeCount = 84L,
                    replyCount = 2L,
                    isLiked = false,
                    authorType = CommentAuthorType.USER,
                ),
                onLikeClick = {},
                onReplyClick = {},
            )
        }
    }
}

@Preview(
    name = "Artist Comment",
    showBackground = true,
)
@Composable
private fun ArtistCommentItemPreview() {
    val context = LocalContext.current
    val profileImageUrl =
        "android.resource://${context.packageName}/${DesignSystemR.drawable.img_profile}"

    MotifTheme {
        Box(
            modifier = Modifier
                .background(MotifTheme.colors.background)
                .padding(20.dp),
        ) {
            CommentItem(
                comment = Comment(
                    id = 2L,
                    profileImageUrl = profileImageUrl,
                    nickname = "YEJI",
                    content = "다들 너무 잘 따라 해줘서 고마워요 🖤",
                    createdAt = System.currentTimeMillis() - 30 * 60_000L,
                    likeCount = 2_841L,
                    replyCount = 32L,
                    isLiked = true,
                    authorType = CommentAuthorType.ARTIST,
                ),
                onLikeClick = {},
                onReplyClick = {},
            )
        }
    }
}