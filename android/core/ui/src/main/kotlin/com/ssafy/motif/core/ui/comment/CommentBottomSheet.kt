package com.ssafy.motif.core.ui.comment

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ssafy.motif.core.designsystem.component.HorizontalDivider
import com.ssafy.motif.core.designsystem.component.Icon
import com.ssafy.motif.core.designsystem.component.Surface
import com.ssafy.motif.core.designsystem.component.Text
import com.ssafy.motif.core.designsystem.component.bottomsheet.ModalBottomSheet
import com.ssafy.motif.core.designsystem.component.bottomsheet.rememberModalBottomSheetState
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.core.model.comment.Comment
import com.ssafy.motif.core.model.comment.Reply
import com.ssafy.motif.core.designsystem.R as DesignSystemR

@Composable
fun CommentBottomSheet(
    commentCount: Long,
    comments: List<Comment>,
    repliesByCommentId: Map<Long, List<Reply>>,
    expandedCommentIds: Set<Long>,
    onDismissRequest: () -> Unit,
    onCommentLikeClick: (Long) -> Unit,
    onReplyClick: (Long) -> Unit,
    onReplyLikeClick: (Long) -> Unit,
    formatCreatedAt: (Long) -> String,
    modifier: Modifier = Modifier,
) {
    val sheetState =
        rememberModalBottomSheetState(
            skipPartiallyExpanded = false,
            partiallyExpandedFraction = 0.6f,
        )

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        CommentSheetContent(
            commentCount = commentCount,
            comments = comments,
            repliesByCommentId = repliesByCommentId,
            expandedCommentIds = expandedCommentIds,
            onDismissClick = onDismissRequest,
            onCommentLikeClick = onCommentLikeClick,
            onReplyClick = onReplyClick,
            onReplyLikeClick = onReplyLikeClick,
            formatCreatedAt = formatCreatedAt,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
    }
}

@Composable
private fun CommentSheetContent(
    commentCount: Long,
    comments: List<Comment>,
    repliesByCommentId: Map<Long, List<Reply>>,
    expandedCommentIds: Set<Long>,
    onDismissClick: () -> Unit,
    onCommentLikeClick: (Long) -> Unit,
    onReplyClick: (Long) -> Unit,
    onReplyLikeClick: (Long) -> Unit,
    formatCreatedAt: (Long) -> String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.92f),
    ) {
        CommentSheetHeader(
            commentCount = commentCount,
            onDismissClick = onDismissClick,
        )

        HorizontalDivider(
            color = Color.White.copy(alpha = 0.35f),
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            comments.forEach { comment ->
                item(
                    key = "comment_${comment.id}",
                ) {
                    CommentItem(
                        comment = comment,
                        onLikeClick = {
                            onCommentLikeClick(comment.id)
                        },
                        onReplyClick = {
                            onReplyClick(comment.id)
                        },
                        formatCreatedAt = formatCreatedAt,
                    )
                }

                if (comment.id in expandedCommentIds) {
                    val replies =
                        repliesByCommentId[comment.id]
                            .orEmpty()

                    items(
                        items = replies,
                        key = { reply ->
                            "reply_${reply.id}"
                        },
                    ) { reply ->
                        ReplyItem(
                            reply = reply,
                            onLikeClick = {
                                onReplyLikeClick(reply.id)
                            },
                            formatCreatedAt = formatCreatedAt,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentSheetHeader(
    commentCount: Long,
    onDismissClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "댓글",
            color = MotifTheme.colors.typo,
            style = MotifTheme.typography.titleSB18,
        )

        Spacer(
            modifier = Modifier.width(12.dp),
        )

        Text(
            text = formatCount(commentCount),
            color = MotifTheme.colors.typo,
            style = MotifTheme.typography.bodySB16,
        )

        Spacer(
            modifier = Modifier.weight(1f),
        )

        Icon(
            painter = painterResource(
                DesignSystemR.drawable.ic_close_24,
            ),
            contentDescription = "닫기",
            tint = MotifTheme.colors.typo,
            modifier = Modifier
                .size(28.dp)
                .clickable(
                    interactionSource = remember {
                        MutableInteractionSource()
                    },
                    indication = null,
                    onClick = onDismissClick,
                ),
        )
    }
}

@Composable
private fun CommentItem(
    comment: Comment,
    onLikeClick: () -> Unit,
    onReplyClick: () -> Unit,
    formatCreatedAt: (Long) -> String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = 20.dp,
                end = 20.dp,
                top = 20.dp,
                bottom = 16.dp,
            ),
        verticalAlignment = Alignment.Top,
    ) {
        AsyncImage(
            model = comment.profileImageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape),
        )

        Spacer(
            modifier = Modifier.width(16.dp),
        )

        Column(
            modifier = Modifier.weight(1f),
        ) {
            CommentAuthor(
                nickname = comment.nickname,
                createdAt = formatCreatedAt(
                    comment.createdAt,
                ),
            )

            Spacer(
                modifier = Modifier.height(8.dp),
            )

            Text(
                text = comment.content,
                color = MotifTheme.colors.typo,
                style = MotifTheme.typography.bodyR16,
            )

            Spacer(
                modifier = Modifier.height(16.dp),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CommentAction(
                    iconRes = if (comment.isLiked) {
                        DesignSystemR.drawable.ic_filled_heart_24
                    } else {
                        DesignSystemR.drawable.ic_heart_24
                    },
                    count = comment.likeCount,
                    tint = if (comment.isLiked) {
                        MotifTheme.colors.pink
                    } else {
                        MotifTheme.colors.typo
                    },
                    onClick = onLikeClick,
                )

                Spacer(
                    modifier = Modifier.width(28.dp),
                )

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

@Composable
private fun ReplyItem(
    reply: Reply,
    onLikeClick: () -> Unit,
    formatCreatedAt: (Long) -> String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = 72.dp,
                end = 20.dp,
                top = 12.dp,
                bottom = 12.dp,
            ),
        verticalAlignment = Alignment.Top,
    ) {
        AsyncImage(
            model = reply.profileImageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape),
        )

        Spacer(
            modifier = Modifier.width(12.dp),
        )

        Column(
            modifier = Modifier.weight(1f),
        ) {
            CommentAuthor(
                nickname = reply.nickname,
                createdAt = formatCreatedAt(
                    reply.createdAt,
                ),
            )

            Spacer(
                modifier = Modifier.height(6.dp),
            )

            Text(
                text = reply.content,
                color = MotifTheme.colors.typo,
                style = MotifTheme.typography.bodyR14,
            )

            Spacer(
                modifier = Modifier.height(12.dp),
            )

            CommentAction(
                iconRes = if (reply.isLiked) {
                    DesignSystemR.drawable.ic_filled_heart_24
                } else {
                    DesignSystemR.drawable.ic_heart_24
                },
                count = reply.likeCount,
                tint = if (reply.isLiked) {
                    MotifTheme.colors.pink
                } else {
                    MotifTheme.colors.typo
                },
                onClick = onLikeClick,
            )
        }
    }
}

@Composable
private fun CommentAuthor(
    nickname: String,
    createdAt: String,
    modifier: Modifier = Modifier,
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

        Spacer(
            modifier = Modifier.width(12.dp),
        )

        Text(
            text = createdAt,
            color = MotifTheme.colors.gray500,
            style = MotifTheme.typography.bodyR14,
        )
    }
}

@Composable
private fun CommentAction(
    iconRes: Int,
    count: Long,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.clickable(
            interactionSource = remember {
                MutableInteractionSource()
            },
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
            text = formatCount(count),
            color = MotifTheme.colors.typo,
            style = MotifTheme.typography.bodyR14,
        )
    }
}

private fun formatCount(
    count: Long,
): String =
    when {
        count >= 1_000_000 -> {
            val value = count / 1_000_000f

            if (value % 1f == 0f) {
                "${value.toInt()}m"
            } else {
                "%.1fm".format(value)
            }
        }

        count >= 1_000 -> {
            val value = count / 1_000f

            if (value % 1f == 0f) {
                "${value.toInt()}k"
            } else {
                "%.1fk".format(value)
            }
        }

        else -> {
            count.toString()
        }
    }

@Preview(
    name = "Comment Sheet Content",
    showBackground = true,
)
@Composable
private fun CommentSheetContentPreview() {
    val context = LocalContext.current

    val profileUrl =
        "android.resource://${context.packageName}/${DesignSystemR.drawable.img_profile}"

    MotifTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MotifTheme.colors.black),
        ) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.6f),
                shape = RoundedCornerShape(
                    topStart = 28.dp,
                    topEnd = 28.dp,
                ),
                color = MotifTheme.colors.background,
                contentColor = MotifTheme.colors.typo,
            ) {
                CommentSheetContent(
                    commentCount = 633L,
                    comments = previewComments(
                        profileUrl = profileUrl,
                    ),
                    repliesByCommentId = mapOf(
                        1L to previewReplies(
                            profileUrl = profileUrl,
                        ),
                    ),
                    expandedCommentIds = emptySet(),
                    onDismissClick = {},
                    onCommentLikeClick = {},
                    onReplyClick = {},
                    onReplyLikeClick = {},
                    formatCreatedAt = {
                        "5일"
                    },
                )
            }
        }
    }
}

@Preview(
    name = "Comment Bottom Sheet",
    showBackground = true,
)
@Composable
private fun CommentBottomSheetPreview() {
    val context = LocalContext.current

    val profileUrl =
        "android.resource://${context.packageName}/${DesignSystemR.drawable.img_profile}"

    MotifTheme {
        CommentBottomSheet(
            commentCount = 633L,
            comments = previewComments(
                profileUrl = profileUrl,
            ),
            repliesByCommentId = mapOf(
                1L to previewReplies(
                    profileUrl = profileUrl,
                ),
            ),
            expandedCommentIds = emptySet(),
            onDismissRequest = {},
            onCommentLikeClick = {},
            onReplyClick = {},
            onReplyLikeClick = {},
            formatCreatedAt = {
                "5일"
            },
        )
    }
}

private fun previewComments(
    profileUrl: String,
): List<Comment> =
    listOf(
        Comment(
            id = 1L,
            profileImageUrl = profileUrl,
            nickname = "yeji_lover",
            content = "이 챌린지 예지랑 너무 잘 어울린다 🔥",
            createdAt = 0L,
            likeCount = 742L,
            replyCount = 8L,
            isLiked = false,
        ),
        Comment(
            id = 2L,
            profileImageUrl = profileUrl,
            nickname = "kkongdance",
            content = "이거 보고 바로 챌린지 찍으러 갑니다",
            createdAt = 0L,
            likeCount = 742L,
            replyCount = 8L,
            isLiked = false,
        ),
        Comment(
            id = 3L,
            profileImageUrl = profileUrl,
            nickname = "dancing_mandu",
            content = "😂😂😂",
            createdAt = 0L,
            likeCount = 742L,
            replyCount = 8L,
            isLiked = true,
        ),
    )

private fun previewReplies(
    profileUrl: String,
): List<Reply> =
    listOf(
        Reply(
            id = 101L,
            commentId = 1L,
            profileImageUrl = profileUrl,
            nickname = "midzy_01",
            content = "진짜 너무 잘 어울려요 ㅋㅋ",
            createdAt = 0L,
            likeCount = 12L,
            isLiked = false,
        ),
        Reply(
            id = 102L,
            commentId = 1L,
            profileImageUrl = profileUrl,
            nickname = "yeji_best",
            content = "저도 이거 보고 찍으려고요 🔥",
            createdAt = 0L,
            likeCount = 4L,
            isLiked = false,
        ),
    )