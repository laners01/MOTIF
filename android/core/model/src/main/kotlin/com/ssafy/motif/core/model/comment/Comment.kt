package com.ssafy.motif.core.model.comment

data class Comment(
    val id: Long,
    val profileImageUrl: String,
    val nickname: String,
    val content: String,
    val createdAt: Long,
    val likeCount: Long,
    val replyCount: Long,
    val isLiked: Boolean,
    val authorType: CommentAuthorType,
)