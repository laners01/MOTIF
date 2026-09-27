package com.ssafy.motif.core.data.mapper

import com.ssafy.motif.core.data.model.CursorPage
import com.ssafy.motif.core.model.artist.Artist
import com.ssafy.motif.core.model.motif.Asset
import com.ssafy.motif.core.model.motif.AssetAvailability
import com.ssafy.motif.core.model.motif.AssetType
import com.ssafy.motif.core.model.motif.SavedMotif
import com.ssafy.motif.core.model.shop.ArtistPass
import com.ssafy.motif.core.model.shop.MotifPassStatus
import com.ssafy.motif.core.model.shop.MyMotifPass
import com.ssafy.motif.core.model.shop.PassBanner
import com.ssafy.motif.core.model.shop.PassPlan
import com.ssafy.motif.core.model.shop.ShopArtistSection
import com.ssafy.motif.core.network.model.artist.ArtistSummaryResponse
import com.ssafy.motif.core.network.model.motif.ArtistMotifPassResponse
import com.ssafy.motif.core.network.model.motif.MotifAssetAccessResponse
import com.ssafy.motif.core.network.model.motif.MotifAssetDetailResponse
import com.ssafy.motif.core.network.model.motif.MotifAssetListResponse
import com.ssafy.motif.core.network.model.motif.MotifAssetTypeResponse
import com.ssafy.motif.core.network.model.motif.MotifPassPriceResponse
import com.ssafy.motif.core.network.model.motif.MotifPassStatusResponse
import com.ssafy.motif.core.network.model.motif.MotifPurchasedAssetResponse
import com.ssafy.motif.core.network.model.motif.MotifResultDetailResponse
import com.ssafy.motif.core.network.model.motif.MotifResultItemResponse
import com.ssafy.motif.core.network.model.motif.MotifResultListResponse
import com.ssafy.motif.core.network.model.motif.MotifShopAssetResponse
import com.ssafy.motif.core.network.model.motif.MotifShopResponse
import com.ssafy.motif.core.network.model.motif.MyMotifPassListResponse
import com.ssafy.motif.core.network.model.motif.MyMotifPassResponse
import com.ssafy.motif.core.network.model.motif.ShopBannerListResponse
import com.ssafy.motif.core.network.model.motif.ShopBannerResponse
import java.math.BigDecimal
import java.time.Instant

internal fun MotifAssetListResponse.toModel(): List<Asset> =
    list.map { response ->
        response.toModel()
    }

private fun MotifPurchasedAssetResponse.toModel(): Asset =
    Asset(
        id = assetSeq,
        thumbnailUrl = thumbnailUrl,
        title = title,
        artist = artist.toArtist(),
        availability = AssetAvailability.PURCHASED,
        description = description,
        durationMs = durationMs,
        type = assetType.toModel(),
        isPremium = assetType == MotifAssetTypeResponse.PREMIUM,
    )

internal fun MotifAssetDetailResponse.toModel(): Asset =
    Asset(
        id = assetSeq,
        thumbnailUrl = thumbnailUrl,
        title = title,
        artist = artist.toArtist(),
        availability = myAccess.toModel(),
        description = description,
        durationMs = durationMs,
        type = assetType.toModel(),
        isPremium = assetType == MotifAssetTypeResponse.PREMIUM,
        price = price?.amount.toPriceInt(),
        currency = price?.currency ?: DEFAULT_CURRENCY,
        previewVideoUrl = previewVideoUrl,
        purchasable = purchasable,
    )

internal fun MotifShopResponse.toModel():
        List<ShopArtistSection> =
    list.map { response ->
        val artist = response.artist.toArtist()

        ShopArtistSection(
            artist = artist,
            hasPass = response.passActive,
            assets = response.assets.map { asset ->
                asset.toModel(
                    artist = artist,
                )
            },
        )
    }

private fun MotifShopAssetResponse.toModel(
    artist: Artist,
): Asset =
    Asset(
        id = assetSeq,
        thumbnailUrl = thumbnailUrl,
        title = title,
        artist = artist,
        availability = myAccess.toModel(),
        description = description,
        durationMs = durationMs,
        type = assetType.toModel(),
        isPremium = assetType == MotifAssetTypeResponse.PREMIUM,
    )

internal fun ArtistMotifPassResponse.toModel():
        ArtistPass =
    ArtistPass(
        artistId = artistSeq,
        bannerImageUrl = bannerImageUrl.orEmpty(),
        headline = PASS_HEADLINE,
        description = PASS_DESCRIPTION,
        plans = prices.map { price ->
            price.toModel()
        },
        isActive = passActive,
        expiresAt = expiresDttm.toEpochMilliOrNull(),
    )

private fun MotifPassPriceResponse.toModel(): PassPlan {
    val totalPrice = amount.toPriceInt()

    val monthlyPrice =
        if (durationMonths > 0) {
            totalPrice / durationMonths
        } else {
            totalPrice
        }

    return PassPlan(
        // 서버 응답에는 별도 planSeq가 없다.
        // 구매 API 연결 전까지 durationMonths를 UI identity로 사용한다.
        id = durationMonths.toLong(),
        months = durationMonths,
        monthlyPrice = monthlyPrice,
        totalPrice = totalPrice,
        currency = currency,
    )
}

internal fun MotifResultListResponse.toModel():
        CursorPage<SavedMotif> =
    CursorPage(
        items = list.map { response ->
            response.toModel()
        },
        previousCursor = prevCursor,
        nextCursor = nextCursor,
        hasPrevious = hasPrevious,
        hasNext = hasNext,
    )

private fun MotifResultItemResponse.toModel():
        SavedMotif =
    SavedMotif(
        id = resultSeq,

        // 목록 응답에는 재생 URL과 문구가 없다.
        // 실제 player 선택 시 상세 API 결과로 교체한다.
        videoUrl = "",
        thumbnailUrl = thumbnailUrl,
        description = "",
        artist = artist.toArtist(),
        savedAt = regDttm.toEpochMilli(),
        isRepresentative = isRep,
    )

internal fun MotifResultDetailResponse.toModel():
        SavedMotif =
    SavedMotif(
        id = resultSeq,
        videoUrl = videoUrl,
        thumbnailUrl = thumbnailUrl,
        description = description.orEmpty(),
        artist = artist.toArtist(),
        savedAt = regDttm.toEpochMilli(),
        isRepresentative = isRep,
        durationMs = durationMs,
        assetId = asset.assetSeq,
        assetTitle = asset.title,
    )

internal fun MyMotifPassListResponse.toModel():
        List<MyMotifPass> =
    list.map { response ->
        response.toModel()
    }

private fun MyMotifPassResponse.toModel():
        MyMotifPass =
    MyMotifPass(
        artistId = artistSeq,
        artist = artist.toArtist(),
        status = status.toModel(),
        startedAt = startedDttm.toEpochMilli(),
        expiresAt = expiresDttm.toEpochMilli(),
        durationMonths = durationMonths,
        canceledAt = canceledDttm.toEpochMilliOrNull(),
    )

internal fun ShopBannerListResponse.toModel():
        List<PassBanner> =
    list
        .sortedBy { response ->
            response.displayOrder
        }
        .map { response ->
            response.toModel()
        }

private fun ShopBannerResponse.toModel():
        PassBanner =
    PassBanner(
        id = bannerSeq,
        imageUrl = imageUrl,
        displayOrder = displayOrder,
    )

private fun ArtistSummaryResponse.toArtist(): Artist =
    Artist(
        id = artistSeq,
        name = name,
        profileImageUrl = profileImageUrl.orEmpty(),
        groupName = groupName,
    )

private fun MotifAssetAccessResponse.toModel():
        AssetAvailability =
    when (this) {
        MotifAssetAccessResponse.OWNED ->
            AssetAvailability.PURCHASED

        MotifAssetAccessResponse.PASS ->
            AssetAvailability.SUBSCRIPTION

        MotifAssetAccessResponse.NONE ->
            AssetAvailability.LOCKED
    }

private fun MotifAssetTypeResponse.toModel():
        AssetType =
    when (this) {
        MotifAssetTypeResponse.NORMAL ->
            AssetType.NORMAL

        MotifAssetTypeResponse.SECRET ->
            AssetType.SECRET

        MotifAssetTypeResponse.PREMIUM ->
            AssetType.PREMIUM
    }

private fun MotifPassStatusResponse.toModel():
        MotifPassStatus =
    when (this) {
        MotifPassStatusResponse.ACTIVE ->
            MotifPassStatus.ACTIVE

        MotifPassStatusResponse.EXPIRED ->
            MotifPassStatus.EXPIRED

        MotifPassStatusResponse.REFUNDED ->
            MotifPassStatus.REFUNDED
    }

private fun String?.toPriceInt(): Int =
    this
        ?.let { value ->
            runCatching {
                BigDecimal(value)
                    .toInt()
            }.getOrDefault(0)
        }
        ?: 0

private fun String?.toEpochMilliOrNull(): Long? =
    this?.let { value ->
        runCatching {
            Instant
                .parse(value)
                .toEpochMilli()
        }.getOrNull()
    }

private fun String.toEpochMilli(): Long =
    runCatching {
        Instant
            .parse(this)
            .toEpochMilli()
    }.getOrDefault(0L)

private const val DEFAULT_CURRENCY = "KRW"

/*
 * 서버 데이터가 아니라 서비스 UI copy.
 * 기존 Mock copy와 다른 문구를 사용하고 있다면 이 두 값만 기존 문자열 리소스로 이동하면 된다.
 */
private const val PASS_HEADLINE =
    "좋아하는 아티스트의 MOTIF를 더 자유롭게 이용해보세요."

private const val PASS_DESCRIPTION =
    "PASS 이용 중 일반·시크릿 에셋을 이용할 수 있으며, 프리미엄 에셋은 별도 구매가 필요해요."