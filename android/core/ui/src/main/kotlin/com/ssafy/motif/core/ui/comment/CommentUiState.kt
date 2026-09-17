package com.ssafy.motif.core.ui.comment

import com.ssafy.motif.core.model.comment.Comment

sealed interface CommentUiState {

    data object Loading : CommentUiState

    data class Comments(
        val comments: List<Comment>,
    ) : CommentUiState

    data class Replies(
        val parentComment: Comment,
        val replies: List<Comment>,
    ) : CommentUiState
}