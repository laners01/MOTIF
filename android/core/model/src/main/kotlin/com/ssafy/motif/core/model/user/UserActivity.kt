package com.ssafy.motif.core.model.user

data class UserActivity(
    val target: UserActivityTarget,
    val isLiked: Boolean,
    val myComment: UserActivityComment?,
    val replies: List<UserActivityReply>,
    val lastInteractedAt: Long,
)

data class UserActivityTarget(
    val type: UserActivityTargetType,
    val id: Long,
    val thumbnailUrl: String,
    val viewCount: Long,
    val likeCount: Long,
)

enum class UserActivityTargetType {
    VIDEO,
    CHALLENGE,
}

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