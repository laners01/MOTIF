package com.ssafy.motif.core.network.model.artist

import kotlinx.serialization.Serializable

@Serializable
data class ArtistSummaryResponse(
    val artistSeq: Long,
    val name: String,
    val groupName: String?,
    val profileImageUrl: String?,
)

@Serializable
data class ArtistSubscriptionSummaryResponse(
    val artistSeq: Long,
    val name: String,
    val groupName: String?,
    val profileImageUrl: String?,
    val isSubscribed: Boolean,
)

@Serializable
data class ArtistRankingResponse(
    val list: List<ArtistRankingItemResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean,
    val hasPrevious: Boolean,
)

@Serializable
data class ArtistRankingItemResponse(
    val rank: Int,
    val rankDelta: Int?,
    val artistSeq: Long,
    val name: String,
    val groupName: String?,
    val profileImageUrl: String?,
    val isSubscribed: Boolean,
)

@Serializable
enum class ArtistRankingFilter {
    ALL,
    SUBSCRIBED,
    UNSUBSCRIBED,
}

@Serializable
data class ArtistSearchResponse(
    val list: List<ArtistSubscriptionSummaryResponse>,
)

@Serializable
data class ArtistDetailResponse(
    val artistSeq: Long,
    val name: String,
    val groupName: String?,
    val profileImageUrl: String?,
    val bio: String,
    val subscriberCount: Long,
    val challengeCount: Long,
    val isSubscribed: Boolean,
    val subscribedDays: Int?,
    val pass: ArtistPassResponse,
    val myMotifVideos: List<ArtistMotifVideoResponse>,
)

@Serializable
data class ArtistPassResponse(
    val active: Boolean,
    val expiresDttm: String?,
)

@Serializable
data class ArtistMotifVideoResponse(
    val resultSeq: Long,
    val videoUrl: String,
    val thumbnailUrl: String,
    val durationMs: Long,
)

@Serializable
data class ArtistChallengeListResponse(
    val list: List<ArtistChallengeResponse>,
)

@Serializable
data class ArtistChallengeResponse(
    val challengeSeq: Long,
    val thumbnailUrl: String,
    val viewCount: Long,
)

@Serializable
data class ArtistNoticeListResponse(
    val list: List<ArtistNoticeResponse>,
    val prevCursor: String?,
    val nextCursor: String?,
    val hasPrevious: Boolean,
    val hasNext: Boolean,
)

@Serializable
data class ArtistNoticeResponse(
    val noticeSeq: Long,
    val artist: ArtistSummaryResponse,
    val content: String,
    val images: List<ArtistNoticeImageResponse>,
    val likeCount: Long,
    val commentCount: Long,
    val isLiked: Boolean,
    val regDttm: String,
)

@Serializable
data class ArtistNoticeImageResponse(
    val imageUrl: String,
    val displayOrder: Int,
)

@Serializable
data class ArtistSubscriptionResponse(
    val isSubscribed: Boolean,
    val subscriberCount: Long,
)

@Serializable
data class ArtistListResponse(
    val list: List<ArtistSubscriptionSummaryResponse>,
)