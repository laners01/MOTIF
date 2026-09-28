package com.ssafy.motif.core.network.model.user

import com.ssafy.motif.core.network.model.challenge.VideoVisibility
import kotlinx.serialization.Serializable

@Serializable
data class UserOverviewResponse(
    val userSeq: Long,
    val nickname: String,
    val profileImageUrl: String?,
    val repMotif: RepresentativeMotifResponse?,
    val videos: List<UserVideoResponse>,
)

@Serializable
data class RepresentativeMotifResponse(
    val resultSeq: Long,
    val videoUrl: String,
    val thumbnailUrl: String,
)

@Serializable
data class UserVideoResponse(
    val videoSeq: Long,
    val thumbnailUrl: String,
    val viewCount: Long,
    val visibility: VideoVisibility,
)

@Serializable
data class UpdateNicknameResponse(
    val nickname: String,
)

@Serializable
data class ProfileImagePresignResponse(
    val uploadUrl: String,
    val imageKey: String,
    val expiresIn: Long,
)

@Serializable
data class UpdateProfileImageResponse(
    val profileImageUrl: String,
)

@Serializable
data class UserProfileResponse(
    val userSeq: Long,
    val nickname: String,
    val profileImageUrl: String?,
    val subscribedArtists: List<UserArtistResponse>,
)

@Serializable
data class UserArtistResponse(
    val artistSeq: Long,
    val name: String,
    val groupName: String?,
    val profileImageUrl: String?,
)

@Serializable
data class UserSubscriptionListResponse(
    val list: List<UserSubscriptionResponse>,
)

@Serializable
data class UserSubscriptionResponse(
    val artistSeq: Long,
    val name: String,
    val groupName: String?,
    val profileImageUrl: String?,
    val passActive: Boolean,
    val passExpiresDttm: String?,
    val subscribedDays: Int,
    val challengeCount: Long,
    val purchasedAssetCount: Long,
    val totalAssetCount: Long,
)

@Serializable
data class UserVideoListResponse(
    val list: List<UserVideoResponse>,
)

@Serializable
data class UserActivityListResponse(
    val list: List<UserActivityResponse>,
    val prevCursor: String?,
    val nextCursor: String?,
    val hasPrevious: Boolean,
    val hasNext: Boolean,
)

@Serializable
data class UserActivityResponse(
    val target: UserActivityTargetResponse,
    val liked: Boolean,
    val myComment: UserActivityCommentResponse?,
    val replies: List<UserActivityReplyResponse>,
    val lastInteractedDttm: String,
)

@Serializable
data class UserActivityTargetResponse(
    val type: UserActivityTargetTypeResponse,
    val seq: Long,
    val thumbnailUrl: String,
    val viewCount: Long,
    val likeCount: Long,
)

@Serializable
enum class UserActivityTargetTypeResponse {
    VIDEO,
    CHALLENGE,
}

@Serializable
data class UserActivityCommentResponse(
    val commentSeq: Long,
    val content: String,
    val likeCount: Long,
    val replyCount: Long,
    val regDttm: String,
    val parentComment: UserActivityParentCommentResponse?,
)

@Serializable
data class UserActivityParentCommentResponse(
    val commentSeq: Long,
    val author: UserActivityAuthorResponse,
    val content: String,
)

@Serializable
data class UserActivityReplyResponse(
    val commentSeq: Long,
    val author: UserActivityAuthorResponse,
    val content: String,
    val regDttm: String,
)

@Serializable
data class UserActivityAuthorResponse(
    val userSeq: Long,
    val nickname: String,
    val profileImageUrl: String?,
)