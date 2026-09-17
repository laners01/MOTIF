package com.ssafy.motif.core.ui.comment

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ssafy.motif.core.designsystem.component.HorizontalDivider
import com.ssafy.motif.core.designsystem.component.IconButton
import com.ssafy.motif.core.designsystem.component.ModifIconButtonDefaults
import com.ssafy.motif.core.designsystem.component.Text
import com.ssafy.motif.core.designsystem.component.bottomsheet.BottomSheet
import com.ssafy.motif.core.designsystem.component.bottomsheet.BottomSheetDefaults
import com.ssafy.motif.core.designsystem.component.bottomsheet.ModalBottomSheet
import com.ssafy.motif.core.designsystem.component.bottomsheet.SheetState
import com.ssafy.motif.core.designsystem.component.bottomsheet.rememberBottomSheetState
import com.ssafy.motif.core.designsystem.icon.MotifIcons
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.core.model.comment.Comment
import com.ssafy.motif.core.model.comment.CommentAuthorType
import com.ssafy.motif.core.ui.R
import com.ssafy.motif.core.ui.formatter.formatCount
import kotlinx.coroutines.launch

/**
 * 챌린지에서 사용하는 댓글 BottomSheet.
 *
 * Scrim이 없으며 Sheet 바깥 영역과 상호작용할 수 있다.
 */
@Composable
fun CommentBottomSheet(
    commentCount: Long,
    content: CommentListState,
    onDismissRequest: () -> Unit,
    onLikeClick: (Long) -> Unit,
    onReplyClick: (Long) -> Unit,
    onBackClick: () -> Unit,
    onCommentSendClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberBottomSheetState(
        skipPartiallyExpanded = false,
        partiallyExpandedFraction = COMMENT_SHEET_FRACTION,
    ),
) {
    CommentSheet(
        commentCount = commentCount,
        content = content,
        onDismissRequest = onDismissRequest,
        onLikeClick = onLikeClick,
        onReplyClick = onReplyClick,
        onBackClick = onBackClick,
        onCommentSendClick = onCommentSendClick,
        sheetState = sheetState,
        modifier = modifier,
        modal = false,
    )
}

/**
 * 일반 Modal 형태로 사용하는 댓글 BottomSheet.
 */
@Composable
fun ModalCommentBottomSheet(
    commentCount: Long,
    content: CommentListState,
    onDismissRequest: () -> Unit,
    onLikeClick: (Long) -> Unit,
    onReplyClick: (Long) -> Unit,
    onBackClick: () -> Unit,
    onCommentSendClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberBottomSheetState(
        skipPartiallyExpanded = false,
        partiallyExpandedFraction = COMMENT_SHEET_FRACTION,
    ),
) {
    CommentSheet(
        commentCount = commentCount,
        content = content,
        onDismissRequest = onDismissRequest,
        onLikeClick = onLikeClick,
        onReplyClick = onReplyClick,
        onBackClick = onBackClick,
        onCommentSendClick = onCommentSendClick,
        sheetState = sheetState,
        modifier = modifier,
        modal = true,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CommentSheet(
    commentCount: Long,
    content: CommentListState,
    onDismissRequest: () -> Unit,
    onLikeClick: (Long) -> Unit,
    onReplyClick: (Long) -> Unit,
    onBackClick: () -> Unit,
    onCommentSendClick: (String) -> Unit,
    sheetState: SheetState,
    modal: Boolean,
    modifier: Modifier = Modifier,
) {
    CommentSoftInputModeEffect()

    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val isImeVisible = WindowInsets.isImeVisible

    var inputHeightPx by remember {
        mutableIntStateOf(0)
    }

    val inputHeight = with(density) {
        inputHeightPx.toDp()
    }

    val isRepliesPage = content is CommentListState.Replies

    val sheetCount = when (content) {
        is CommentListState.Comments -> commentCount
        is CommentListState.Replies -> content.parentComment.replyCount
    }

    val dismissKeyboard: () -> Unit = {
        focusManager.clearFocus()
    }

    val closeSheet: () -> Unit = {
        dismissKeyboard()

        coroutineScope.launch {
            sheetState.hide()
        }
    }

    val dragHandle: @Composable () -> Unit = {
        Box {
            CommentSheetHeader(
                title = stringResource(
                    if (isRepliesPage) {
                        R.string.comment_sheet_replies_title
                    } else {
                        R.string.comment_sheet_comments_title
                    },
                ),
                count = sheetCount,
                showBackButton = isRepliesPage,
                onBackClick = onBackClick,
                onDismissClick = closeSheet,
            )

            if (isImeVisible) {
                CommentKeyboardScrim(
                    modifier = Modifier.matchParentSize(),
                    onClick = dismissKeyboard,
                )
            }
        }
    }

    val sheetContent: @Composable ColumnScope.() -> Unit = {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            CommentSheetBody(
                content = content,
                onLikeClick = onLikeClick,
                onReplyClick = onReplyClick,
                bottomContentPadding = inputHeight,
                modifier = Modifier.fillMaxSize(),
            )

            if (isImeVisible) {
                CommentKeyboardScrim(
                    modifier = Modifier.matchParentSize(),
                    onClick = dismissKeyboard,
                )
            }

            CommentInput(
                onSendClick = onCommentSendClick,
                placeholder = stringResource(
                    if (isRepliesPage) {
                        R.string.reply_input_placeholder
                    } else {
                        R.string.comment_input_placeholder
                    },
                ),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { size ->
                        inputHeightPx = size.height
                    },
            )
        }
    }

    if (modal) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
            modifier = modifier,
            dragHandle = dragHandle,
            content = sheetContent,
        )
    } else {
        BottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
            modifier = modifier,
            dragHandle = dragHandle,
            content = sheetContent,
        )
    }

    BackHandler(
        enabled = isRepliesPage && !isImeVisible,
    ) {
        onBackClick()
    }
}

@Composable
private fun CommentSheetBody(
    content: CommentListState,
    onLikeClick: (Long) -> Unit,
    onReplyClick: (Long) -> Unit,
    bottomContentPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val commentsListState = rememberLazyListState()

    AnimatedContent(
        targetState = content,
        contentKey = { currentContent ->
            when (currentContent) {
                is CommentListState.Comments -> COMMENTS_CONTENT_KEY
                is CommentListState.Replies -> REPLIES_CONTENT_KEY
            }
        },
        transitionSpec = {
            commentSheetTransition(
                initialContent = initialState,
                targetContent = targetState,
            )
        },
        modifier = modifier,
        label = "CommentSheetContent",
    ) { currentContent ->
        val listState = when (currentContent) {
            is CommentListState.Comments -> commentsListState
            is CommentListState.Replies -> rememberLazyListState()
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                top = 20.dp,
                end = 20.dp,
                bottom = 20.dp + bottomContentPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            when (currentContent) {
                is CommentListState.Comments -> {
                    items(
                        items = currentContent.comments,
                        key = { comment -> comment.id },
                    ) { comment ->
                        CommentItem(
                            comment = comment,
                            onLikeClick = {
                                onLikeClick(comment.id)
                            },
                            onReplyClick = {
                                onReplyClick(comment.id)
                            },
                        )
                    }
                }

                is CommentListState.Replies -> {
                    item(
                        key = currentContent.parentComment.id,
                    ) {
                        CommentItem(
                            comment = currentContent.parentComment,
                            onLikeClick = {
                                onLikeClick(currentContent.parentComment.id)
                            },
                            onReplyClick = null,
                        )
                    }

                    items(
                        items = currentContent.replies,
                        key = { reply -> reply.id },
                    ) { reply ->
                        CommentItem(
                            comment = reply,
                            onLikeClick = {
                                onLikeClick(reply.id)
                            },
                            onReplyClick = null,
                            modifier = Modifier.padding(start = 48.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentKeyboardScrim(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember {
        MutableInteractionSource()
    }

    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = KEYBOARD_SCRIM_ALPHA))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    )
}

@Composable
private fun CommentSheetHeader(
    title: String,
    count: Long,
    onDismissClick: () -> Unit,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = false,
    onBackClick: () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        BottomSheetDefaults.DragHandle()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    vertical = 12.dp,
                    horizontal = 20.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showBackButton) {
                IconButton(
                    painter = painterResource(MotifIcons.BarArrowLeft),
                    size = 20.dp,
                    onClick = onBackClick,
                    colors = ModifIconButtonDefaults.iconButtonColors(
                        contentColor = MotifTheme.colors.typo,
                    ),
                )

                Spacer(modifier = Modifier.width(16.dp))
            }

            Text(
                text = title,
                color = MotifTheme.colors.typo,
                style = MotifTheme.typography.titleSB18,
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = count.formatCount(),
                color = MotifTheme.colors.typo,
                style = MotifTheme.typography.bodySB16,
            )

            Spacer(modifier = Modifier.weight(1f))

            IconButton(
                painter = painterResource(MotifIcons.Close),
                size = 28.dp,
                onClick = onDismissClick,
                colors = ModifIconButtonDefaults.iconButtonColors(
                    contentColor = MotifTheme.colors.typo,
                ),
            )
        }

        HorizontalDivider(
            color = MotifTheme.colors.gray500,
        )
    }
}

@Composable
private fun CommentSoftInputModeEffect() {
    val context = LocalContext.current
    val activity = remember(context) {
        context.findActivity()
    }

    DisposableEffect(activity) {
        val window = activity?.window

        if (window == null) {
            onDispose {}
        } else {
            val previousSoftInputMode = window.attributes.softInputMode

            window.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING,
            )

            onDispose {
                window.setSoftInputMode(previousSoftInputMode)
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

private fun commentSheetTransition(
    initialContent: CommentListState,
    targetContent: CommentListState,
): ContentTransform {
    val direction =
        if (
            initialContent is CommentListState.Comments &&
            targetContent is CommentListState.Replies
        ) {
            1
        } else {
            -1
        }

    return (
            slideInHorizontally(
                animationSpec = tween(PAGE_TRANSITION_DURATION),
            ) { width ->
                width * direction
            } +
                    fadeIn(
                        animationSpec = tween(PAGE_TRANSITION_DURATION),
                    )
            ).togetherWith(
            slideOutHorizontally(
                animationSpec = tween(PAGE_TRANSITION_DURATION),
            ) { width ->
                -width * direction
            } +
                    fadeOut(
                        animationSpec = tween(PAGE_TRANSITION_DURATION),
                    ),
        )
}

@Preview(
    name = "CommentBottomSheet",
    showBackground = true,
)
@Composable
private fun CommentBottomSheetPreview() {
    val now = System.currentTimeMillis()

    val comments = listOf(
        Comment(
            id = 1L,
            profileImageUrl = "",
            nickname = "dance_with_me",
            content = "이 부분 안무 진짜 중독성 있다 ㅋㅋ",
            createdAt = now - 5 * 60_000L,
            likeCount = 84L,
            replyCount = 2L,
            isLiked = false,
            authorType = CommentAuthorType.USER,
        ),
        Comment(
            id = 2L,
            profileImageUrl = "",
            nickname = "YEJI",
            content = "다들 너무 잘 따라 해줘서 고마워요 🖤",
            createdAt = now - 30 * 60_000L,
            likeCount = 2_841L,
            replyCount = 32L,
            isLiked = true,
            authorType = CommentAuthorType.ARTIST,
        ),
        Comment(
            id = 3L,
            profileImageUrl = "",
            nickname = "motif_me",
            content = "나도 방금 찍어봤는데 생각보다 어렵네 😂",
            createdAt = now - 2 * 60 * 60_000L,
            likeCount = 16L,
            replyCount = 1L,
            isLiked = false,
            authorType = CommentAuthorType.CURRENT_USER,
        ),
    )

    MotifTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.DarkGray),
        ) {
            CommentBottomSheet(
                commentCount = comments.size.toLong(),
                content = CommentListState.Comments(
                    comments = comments,
                ),
                onDismissRequest = {},
                onLikeClick = {},
                onReplyClick = {},
                onBackClick = {},
                onCommentSendClick = {},
            )
        }
    }
}

@Preview(
    name = "ModalCommentBottomSheet - Replies",
    showBackground = true,
)
@Composable
private fun ModalCommentBottomSheetPreview() {
    val now = System.currentTimeMillis()

    val parentComment = Comment(
        id = 1L,
        profileImageUrl = "",
        nickname = "YEJI",
        content = "다들 너무 잘 따라 해줘서 고마워요 🖤",
        createdAt = now - 30 * 60_000L,
        likeCount = 2_841L,
        replyCount = 2L,
        isLiked = true,
        authorType = CommentAuthorType.ARTIST,
    )

    val replies = listOf(
        Comment(
            id = 2L,
            profileImageUrl = "",
            nickname = "yeji_fan",
            content = "직접 댓글 남겨준 거 실화인가요 😭",
            createdAt = now - 15 * 60_000L,
            likeCount = 127L,
            replyCount = 0L,
            isLiked = true,
            authorType = CommentAuthorType.USER,
        ),
        Comment(
            id = 3L,
            profileImageUrl = "",
            nickname = "motif_daily",
            content = "다음 챌린지도 기다릴게요!",
            createdAt = now - 3 * 60 * 60_000L,
            likeCount = 38L,
            replyCount = 0L,
            isLiked = false,
            authorType = CommentAuthorType.USER,
        ),
    )

    MotifTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.DarkGray),
        ) {
            ModalCommentBottomSheet(
                commentCount = 10L,
                content = CommentListState.Replies(
                    parentComment = parentComment,
                    replies = replies,
                ),
                onDismissRequest = {},
                onLikeClick = {},
                onReplyClick = {},
                onBackClick = {},
                onCommentSendClick = {},
            )
        }
    }
}

private const val COMMENT_SHEET_FRACTION = 0.6f
private const val PAGE_TRANSITION_DURATION = 250
private const val COMMENTS_CONTENT_KEY = "comments"
private const val REPLIES_CONTENT_KEY = "replies"
private const val KEYBOARD_SCRIM_ALPHA = 0.45f