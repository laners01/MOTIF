package com.ssafy.motif.feature.artistdetail.impl.artistdetail

import com.ssafy.motif.core.ui.comment.CommentListState

internal sealed interface CommentSheetUiState {

    data object Hidden : CommentSheetUiState

    data object Loading : CommentSheetUiState

    data class Success(
        val commentCount: Long,
        val content: CommentListState,
    ) : CommentSheetUiState

    data class Error(
        val exception: Throwable,
    ) : CommentSheetUiState
}