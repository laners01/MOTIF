package com.ssafy.motif.core.ui.comment

import android.view.WindowManager
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import coil.compose.AsyncImage
import com.ssafy.motif.core.designsystem.component.HorizontalDivider
import com.ssafy.motif.core.designsystem.component.Icon
import com.ssafy.motif.core.designsystem.component.Surface
import com.ssafy.motif.core.designsystem.component.Text
import com.ssafy.motif.core.designsystem.component.bottomsheet.BottomSheetDefaults
import com.ssafy.motif.core.designsystem.component.bottomsheet.ModalBottomSheet
import com.ssafy.motif.core.designsystem.component.bottomsheet.SheetState
import com.ssafy.motif.core.designsystem.component.bottomsheet.SheetValue
import com.ssafy.motif.core.designsystem.component.bottomsheet.rememberModalBottomSheetState
import com.ssafy.motif.core.designsystem.theme.MotifColors
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.core.model.comment.Comment
import com.ssafy.motif.core.model.comment.CommentAuthorType
import com.ssafy.motif.core.model.comment.Reply
import kotlinx.coroutines.launch
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

    val listState =
        rememberLazyListState()

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        dragHandle = {
            CommentSheetHeader(
                commentCount = commentCount,
                onDismissClick = onDismissRequest,
            )
        },
        modifier = modifier,
    ) {
        CommentSheetContent(
            comments = comments,
            repliesByCommentId = repliesByCommentId,
            expandedCommentIds = expandedCommentIds,
            sheetState = sheetState,
            listState = listState,
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
    comments: List<Comment>,
    repliesByCommentId: Map<Long, List<Reply>>,
    expandedCommentIds: Set<Long>,
    sheetState: SheetState,
    listState: LazyListState,
    onCommentLikeClick: (Long) -> Unit,
    onReplyClick: (Long) -> Unit,
    onReplyLikeClick: (Long) -> Unit,
    formatCreatedAt: (Long) -> String,
    modifier: Modifier = Modifier,
) {
    val prioritizedComments =
        remember(comments) {
            prioritizeComments(
                comments = comments,
            )
        }

    val nestedScrollConnection =
        rememberCommentSheetNestedScrollConnection(
            sheetState = sheetState,
            listState = listState,
        )

    Column(
        modifier = modifier
            .fillMaxWidth(),
    ) {
        HorizontalDivider(color = MotifColors.defaultColors().gray500)
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .nestedScroll(
                    nestedScrollConnection,
                ),
        ) {
            prioritizedComments.forEach { comment ->
                item(
                    key = "comment_${comment.id}",
                ) {
                    CommentItem(
                        comment = comment,
                        onLikeClick = {
                            onCommentLikeClick(
                                comment.id,
                            )
                        },
                        onReplyClick = {
                            onReplyClick(
                                comment.id,
                            )
                        },
                        formatCreatedAt = formatCreatedAt,
                    )
                }

                if (comment.id in expandedCommentIds) {
                    val replies =
                        repliesByCommentId[
                            comment.id
                        ].orEmpty()

                    items(
                        items = replies,
                        key = { reply ->
                            "reply_${reply.id}"
                        },
                    ) { reply ->
                        ReplyItem(
                            reply = reply,
                            onLikeClick = {
                                onReplyLikeClick(
                                    reply.id,
                                )
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
private fun rememberCommentSheetNestedScrollConnection(
    sheetState: SheetState,
    listState: LazyListState,
): NestedScrollConnection {
    val coroutineScope =
        rememberCoroutineScope()

    return remember(
        sheetState,
        listState,
        coroutineScope,
    ) {
        object : NestedScrollConnection {

            var isCollapsing = false

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (
                    source != NestedScrollSource.UserInput ||
                    available.y <= 0f
                ) {
                    return Offset.Zero
                }

                val isListAtTop =
                    listState.firstVisibleItemIndex == 0 &&
                            listState.firstVisibleItemScrollOffset == 0

                if (!isListAtTop) {
                    return Offset.Zero
                }

                val canCollapse =
                    sheetState.currentValue ==
                            SheetValue.Expanded ||
                            sheetState.currentValue ==
                            SheetValue.PartiallyExpanded

                if (!canCollapse) {
                    return Offset.Zero
                }

                if (!isCollapsing) {
                    isCollapsing = true

                    coroutineScope.launch {
                        try {
                            when (sheetState.currentValue) {
                                SheetValue.Expanded -> {
                                    sheetState.partialExpand()
                                }

                                SheetValue.PartiallyExpanded -> {
                                    sheetState.hide()
                                }

                                SheetValue.Hidden -> Unit
                            }
                        } finally {
                            isCollapsing = false
                        }
                    }
                }

                /*
                 * LazyColumn이 최상단에서 더 이상 내려갈 수 없을 때
                 * 남은 아래 방향 스크롤을 소비한다.
                 *
                 * 그렇지 않으면 overscroll 효과가 동시에 발생할 수 있다.
                 */
                return Offset(
                    x = 0f,
                    y = available.y,
                )
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
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BottomSheetDefaults.DragHandle()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 20.dp,
                ),
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
                text = formatCount(
                    commentCount,
                ),
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
                authorType = comment.authorType,
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
                    iconRes =
                        if (comment.isLiked) {
                            DesignSystemR.drawable.ic_filled_heart_24
                        } else {
                            DesignSystemR.drawable.ic_heart_24
                        },
                    count = comment.likeCount,
                    tint =
                        if (comment.isLiked) {
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
                    iconRes =
                        DesignSystemR.drawable.ic_message_24,
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
                iconRes =
                    if (reply.isLiked) {
                        DesignSystemR.drawable.ic_filled_heart_24
                    } else {
                        DesignSystemR.drawable.ic_heart_24
                    },
                count = reply.likeCount,
                tint =
                    if (reply.isLiked) {
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
    authorType: CommentAuthorType =
        CommentAuthorType.USER,
) {
    Row(
        modifier = modifier,
        verticalAlignment =
            Alignment.CenterVertically,
    ) {
        Text(
            text = nickname,
            color = MotifTheme.colors.typo,
            style = MotifTheme.typography.bodySB16,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (
            authorType ==
            CommentAuthorType.ARTIST
        ) {
            Spacer(
                modifier = Modifier.width(4.dp),
            )

            Icon(
                painter = painterResource(
                    DesignSystemR.drawable.ic_artist_check_24,
                ),
                contentDescription = "아티스트",
                modifier = Modifier.size(16.dp),
                tint = Color.Unspecified,
            )
        }

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
        modifier = modifier
            .clickable(
                interactionSource =
                    remember {
                        MutableInteractionSource()
                    },
                indication = null,
                onClick = onClick,
            ),
        horizontalArrangement =
            Arrangement.spacedBy(
                6.dp,
            ),
        verticalAlignment =
            Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(
                iconRes,
            ),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = tint,
        )

        Text(
            text = formatCount(
                count,
            ),
            color = MotifTheme.colors.typo,
            style = MotifTheme.typography.bodyR14,
        )
    }
}

private fun prioritizeComments(
    comments: List<Comment>,
): List<Comment> =
    buildList {
        addAll(
            comments.filter { comment ->
                comment.authorType ==
                        CommentAuthorType.ARTIST
            },
        )

        addAll(
            comments.filter { comment ->
                comment.authorType ==
                        CommentAuthorType.CURRENT_USER
            },
        )

        addAll(
            comments.filter { comment ->
                comment.authorType ==
                        CommentAuthorType.USER
            },
        )
    }

private fun formatCount(
    count: Long,
): String =
    when {
        count >= 1_000_000 -> {
            val value =
                count / 1_000_000f

            if (value % 1f == 0f) {
                "${value.toInt()}m"
            } else {
                "%.1fm".format(
                    value,
                )
            }
        }

        count >= 1_000 -> {
            val value =
                count / 1_000f

            if (value % 1f == 0f) {
                "${value.toInt()}k"
            } else {
                "%.1fk".format(
                    value,
                )
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
    val context =
        LocalContext.current

    val profileUrl =
        "android.resource://${context.packageName}/${DesignSystemR.drawable.img_profile}"

    val sheetState =
        rememberModalBottomSheetState(
            skipPartiallyExpanded = false,
            partiallyExpandedFraction = 0.6f,
        )

    MotifTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    MotifTheme.colors.black,
                ),
        ) {
            Surface(
                modifier = Modifier
                    .align(
                        Alignment.BottomCenter,
                    )
                    .fillMaxWidth()
                    .fillMaxHeight(0.6f),
                shape =
                    RoundedCornerShape(
                        topStart = 28.dp,
                        topEnd = 28.dp,
                    ),
                color =
                    MotifTheme.colors.background,
                contentColor =
                    MotifTheme.colors.typo,
            ) {
                CommentSheetContent(
                    comments =
                        previewComments(
                            profileUrl = profileUrl,
                        ),
                    repliesByCommentId =
                        mapOf(
                            30L to previewReplies(
                                profileUrl = profileUrl,
                            ),
                        ),
                    expandedCommentIds =
                        emptySet(),
                    sheetState = sheetState,
                    listState =
                        rememberLazyListState(),
                    onCommentLikeClick = {},
                    onReplyClick = {},
                    onReplyLikeClick = {},
                    formatCreatedAt = {
                        "3시간"
                    },
                )
            }
        }
    }
}

@Preview(
    name = "Comment Bottom Sheet - Reply Expanded",
    showBackground = true,
)
@Composable
private fun CommentBottomSheetPreview() {
    val context =
        LocalContext.current

    val profileUrl =
        "android.resource://${context.packageName}/${DesignSystemR.drawable.img_profile}"

    MotifTheme {
        CommentBottomSheet(
            commentCount = 328L,
            comments =
                previewComments(
                    profileUrl = profileUrl,
                ),
            repliesByCommentId =
                mapOf(
                    30L to previewReplies(
                        profileUrl = profileUrl,
                    ),
                ),
            expandedCommentIds =
                setOf(30L),
            onDismissRequest = {},
            onCommentLikeClick = {},
            onReplyClick = {},
            onReplyLikeClick = {},
            formatCreatedAt = {
                "3시간"
            },
        )
    }
}

private fun previewComments(
    profileUrl: String,
): List<Comment> =
    listOf(
        Comment(
            id = 10L,
            profileImageUrl = profileUrl,
            nickname = "dance_with_me",
            content = "이 부분 안무 진짜 중독성 있다 ㅋㅋ",
            createdAt = 0L,
            likeCount = 84L,
            replyCount = 2L,
            isLiked = false,
            authorType =
                CommentAuthorType.USER,
        ),
        Comment(
            id = 20L,
            profileImageUrl = profileUrl,
            nickname = "motif_me",
            content = "나도 방금 찍어봤는데 생각보다 어렵네 😂",
            createdAt = 0L,
            likeCount = 16L,
            replyCount = 1L,
            isLiked = true,
            authorType =
                CommentAuthorType.CURRENT_USER,
        ),
        Comment(
            id = 30L,
            profileImageUrl = profileUrl,
            nickname = "KARINA",
            content = "다들 너무 잘 따라 해줘서 고마워요 🖤",
            createdAt = 0L,
            likeCount = 2_841L,
            replyCount = 32L,
            isLiked = false,
            authorType =
                CommentAuthorType.ARTIST,
        ),
        Comment(
            id = 40L,
            profileImageUrl = profileUrl,
            nickname = "winterluv",
            content = "영상 보자마자 바로 참여하러 갑니다",
            createdAt = 0L,
            likeCount = 53L,
            replyCount = 0L,
            isLiked = false,
            authorType =
                CommentAuthorType.USER,
        ),
    )

private fun previewReplies(
    profileUrl: String,
): List<Reply> =
    listOf(
        Reply(
            id = 301L,
            commentId = 30L,
            profileImageUrl = profileUrl,
            nickname = "my_karina",
            content = "직접 댓글 남겨준 거 실화인가요 😭",
            createdAt = 0L,
            likeCount = 127L,
            isLiked = true,
        ),
        Reply(
            id = 302L,
            commentId = 30L,
            profileImageUrl = profileUrl,
            nickname = "ae_motif",
            content = "다음 챌린지도 기다릴게요!",
            createdAt = 0L,
            likeCount = 38L,
            isLiked = false,
        ),
    )