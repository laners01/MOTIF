package com.ssafy.motif.core.model.comment

data class Reply(
    val id: Long,
    val commentId: Long,
    val profileImageUrl: String,
    val nickname: String,
    val content: String,
    val createdAt: Long,
    val likeCount: Long,
    val isLiked: Boolean,
)