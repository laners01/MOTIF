package com.ssafy.motif.feature.mypage.impl.mypage

import androidx.compose.runtime.Immutable

@Immutable
internal data class LikedChallengeUiModel(
    val id: Long,
    val thumbnailUrl: String,
    val likeCount: Long,
    val commentState: LikedChallengeCommentUiState,
)

internal sealed interface LikedChallengeCommentUiState {

    data object Empty : LikedChallengeCommentUiState

    data class Written(
        val myComment: MyCommentUiModel,
    ) : LikedChallengeCommentUiState
}

@Immutable
internal data class MyCommentUiModel(
    val content: String,
    val createdAt: Long,
    val likeCount: Long,
    val replyCount: Long,
    val replies: List<MyReplyUiModel>,
)

@Immutable
internal data class MyReplyUiModel(
    val nickname: String,
    val profileImageUrl: String?,
    val content: String,
    val createdAt: Long,
)