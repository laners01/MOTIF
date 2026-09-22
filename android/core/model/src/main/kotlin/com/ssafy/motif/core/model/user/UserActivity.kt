package com.ssafy.motif.core.model.user

data class UserActivity(
    val video: UserActivityVideo,
    val isLiked: Boolean,
    val myComment: UserActivityComment?,
    val replies: List<UserActivityReply>,
    val lastInteractedAt: Long,
)

data class UserActivityVideo(
    val id: Long,
    val thumbnailUrl: String,
    val viewCount: Long,
    val likeCount: Long,
)

data class UserActivityComment(
    val id: Long,
    val content: String,
    val likeCount: Long,
    val replyCount: Long,
    val createdAt: Long,
    val parentComment: UserActivityParentComment?,
)

data class UserActivityParentComment(
    val id: Long,
    val author: UserProfile,
    val content: String,
)

data class UserActivityReply(
    val id: Long,
    val author: UserProfile,
    val content: String,
    val createdAt: Long,
)