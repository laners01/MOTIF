package com.ssafy.motif.feature.home.impl.model

import com.ssafy.motif.core.model.comment.Comment
import com.ssafy.motif.core.model.comment.Reply

internal sealed interface CommentSheetUiState {

    data object Hidden : CommentSheetUiState

    data object Loading : CommentSheetUiState

    data class Success(
        val commentCount: Long,
        val comments: List<Comment>,
        val repliesByCommentId: Map<Long, List<Reply>>,
        val expandedCommentIds: Set<Long>,
    ) : CommentSheetUiState

    data class Error(
        val exception: Throwable,
    ) : CommentSheetUiState
}