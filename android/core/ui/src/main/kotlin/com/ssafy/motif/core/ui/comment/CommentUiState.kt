package com.ssafy.motif.core.ui.comment

import androidx.compose.runtime.Immutable
import com.ssafy.motif.core.model.comment.Comment

@Immutable
data class CommentUiState(
    val page: CommentPage = CommentPage.Comments,
    val comments: List<Comment> = emptyList(),
    val replies: List<Comment> = emptyList(),
    val isLoading: Boolean = false,
)