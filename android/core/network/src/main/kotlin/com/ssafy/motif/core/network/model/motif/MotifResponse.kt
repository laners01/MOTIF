package com.ssafy.motif.core.network.model.motif

import com.ssafy.motif.core.network.model.artist.ArtistSummaryResponse
import kotlinx.serialization.Serializable

@Serializable
data class MotifAssetListResponse(
    val list: List<MotifPurchasedAssetResponse>,
)

@Serializable
data class MotifPurchasedAssetResponse(
    val assetSeq: Long,
    val artist: ArtistSummaryResponse,
    val title: String,
    val thumbnailUrl: String,
    val description: String? = null,
    val durationMs: Long,
    val assetType: MotifAssetTypeResponse,
)

@Serializable
data class MotifAssetDetailResponse(
    val assetSeq: Long,
    val artist: ArtistSummaryResponse,
    val title: String,
    val previewVideoUrl: String,
    val thumbnailUrl: String,
    val description: String? = null,
    val durationMs: Long,
    val assetType: MotifAssetTypeResponse,
    val price: MotifPriceResponse? = null,
    val purchasable: Boolean,
    val myAccess: MotifAssetAccessResponse,
)

@Serializable
data class MotifPriceResponse(
    val amount: String,
    val currency: String,
)

@Serializable
enum class MotifAssetTypeResponse {
    NORMAL,
    SECRET,
    PREMIUM,
}

@Serializable
enum class MotifAssetAccessResponse {
    OWNED,
    PASS,
    NONE,
}

@Serializable
data class MotifShopResponse(
    val list: List<MotifShopArtistResponse>,
)

@Serializable
data class MotifShopArtistResponse(
    val artist: ArtistSummaryResponse,
    val passActive: Boolean,
    val expiresDttm: String? = null,
    val assets: List<MotifShopAssetResponse>,
)

@Serializable
data class MotifShopAssetResponse(
    val assetSeq: Long,
    val title: String,
    val thumbnailUrl: String,
    val description: String? = null,
    val durationMs: Long,
    val assetType: MotifAssetTypeResponse,
    val myAccess: MotifAssetAccessResponse,
)

@Serializable
data class ArtistMotifPassResponse(
    val artistSeq: Long,
    val bannerImageUrl: String? = null,
    val passActive: Boolean,
    val expiresDttm: String? = null,
    val prices: List<MotifPassPriceResponse>,
)

@Serializable
data class MotifPassPriceResponse(
    val durationMonths: Int,
    val amount: String,
    val currency: String,
)

@Serializable
data class MotifResultListResponse(
    val list: List<MotifResultItemResponse>,
    val prevCursor: String? = null,
    val nextCursor: String? = null,
    val hasPrevious: Boolean,
    val hasNext: Boolean,
)

@Serializable
data class MotifResultItemResponse(
    val resultSeq: Long,
    val thumbnailUrl: String,
    val artist: ArtistSummaryResponse,
    val regDttm: String,
    val isRep: Boolean,
)

@Serializable
data class MotifResultDetailResponse(
    val resultSeq: Long,
    val videoUrl: String,
    val thumbnailUrl: String,
    val description: String? = null,
    val durationMs: Long,
    val asset: MotifResultAssetResponse,
    val artist: ArtistSummaryResponse,
    val isRep: Boolean,
    val regDttm: String,
)

@Serializable
data class MotifResultAssetResponse(
    val assetSeq: Long,
    val title: String,
)

@Serializable
data class UpdateMotifResultRequest(
    val description: String? = null,
    val isRep: Boolean? = null,
)

@Serializable
data class MyMotifPassListResponse(
    val list: List<MyMotifPassResponse>,
)

@Serializable
data class MyMotifPassResponse(
    val artistSeq: Long,
    val artist: ArtistSummaryResponse,
    val status: MotifPassStatusResponse,
    val startedDttm: String,
    val expiresDttm: String,
    val durationMonths: Int,
    val canceledDttm: String? = null,
)

@Serializable
enum class MotifPassStatusResponse {
    ACTIVE,
    EXPIRED,
    REFUNDED,
}

@Serializable
data class ShopBannerListResponse(
    val list: List<ShopBannerResponse>,
)

@Serializable
data class ShopBannerResponse(
    val bannerSeq: Long,
    val imageUrl: String,
    val displayOrder: Int,
)