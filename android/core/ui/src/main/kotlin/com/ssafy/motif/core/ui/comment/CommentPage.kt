package com.ssafy.motif.core.ui.comment

import androidx.compose.runtime.Immutable
import com.ssafy.motif.core.model.comment.Comment

@Immutable
sealed interface CommentPage {

    data object Comments : CommentPage

    data class Replies(
        val parentComment: Comment,
    ) : CommentPage
}