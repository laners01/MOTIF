package com.ssafy.motif.core.ui.comment

import com.ssafy.motif.core.model.comment.Comment

sealed interface CommentListState {

    data class Comments(
        val comments: List<Comment>,
    ) : CommentListState

    data class Replies(
        val parentComment: Comment,
        val replies: List<Comment>,
    ) : CommentListState
}