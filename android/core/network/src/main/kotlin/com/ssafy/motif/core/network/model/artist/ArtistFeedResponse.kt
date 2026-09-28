package com.ssafy.motif.core.network.model.artist

import kotlinx.serialization.Serializable

@Serializable
data class ArtistFeedResponse(
    val list: List<ArtistFeedItemResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean,
    val hasPrevious: Boolean,
)

@Serializable
data class ArtistFeedItemResponse(
    val type: ArtistFeedType,
    val regDttm: String,
    val artist: ArtistFeedArtistResponse,
    val asset: ArtistFeedAssetResponse? = null,
    val notice: ArtistFeedNoticeResponse? = null,
)

@Serializable
enum class ArtistFeedType {
    MOTIF_ASSET,
    NOTICE,
}

@Serializable
data class ArtistFeedArtistResponse(
    val artistSeq: Long,
    val name: String,
    val groupName: String? = null,
    val profileImageUrl: String? = null,
)

@Serializable
data class ArtistFeedAssetResponse(
    val assetSeq: Long,
    val title: String,
    val thumbnailUrl: String,
    val durationMs: Long,
)

@Serializable
data class ArtistFeedNoticeResponse(
    val noticeSeq: Long,
    val content: String,
    val images: List<ArtistFeedNoticeImageResponse>,
    val likeCount: Long,
    val commentCount: Long,
    val isLiked: Boolean,
)

@Serializable
data class ArtistFeedNoticeImageResponse(
    val imageUrl: String,
    val displayOrder: Int,
)