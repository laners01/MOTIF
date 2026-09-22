package com.ssafy.motif.core.data.mapper

import com.ssafy.motif.core.data.model.OffsetPage
import com.ssafy.motif.core.model.artist.Artist
import com.ssafy.motif.core.model.artist.ArtistDetail
import com.ssafy.motif.core.model.artist.ArtistFilter
import com.ssafy.motif.core.model.artist.ArtistMotifVideo
import com.ssafy.motif.core.model.artist.ArtistPass
import com.ssafy.motif.core.model.artist.ArtistSubscriptionResult
import com.ssafy.motif.core.model.artist.ArtistWithSubscription
import com.ssafy.motif.core.model.artist.RankedArtist
import com.ssafy.motif.core.network.model.artist.ArtistDetailResponse
import com.ssafy.motif.core.network.model.artist.ArtistListResponse
import com.ssafy.motif.core.network.model.artist.ArtistMotifVideoResponse
import com.ssafy.motif.core.network.model.artist.ArtistPassResponse
import com.ssafy.motif.core.network.model.artist.ArtistRankingFilter
import com.ssafy.motif.core.network.model.artist.ArtistRankingItemResponse
import com.ssafy.motif.core.network.model.artist.ArtistRankingResponse
import com.ssafy.motif.core.network.model.artist.ArtistSearchResponse
import com.ssafy.motif.core.network.model.artist.ArtistSubscriptionResponse
import com.ssafy.motif.core.network.model.artist.ArtistSubscriptionSummaryResponse
import java.time.Instant

internal fun ArtistRankingResponse.toModel(): OffsetPage<RankedArtist> =
    OffsetPage(
        items = list.map { response ->
            response.toModel()
        },
        page = page,
        size = size,
        totalElements = totalElements,
        totalPages = totalPages,
        hasNext = hasNext,
        hasPrevious = hasPrevious,
    )

private fun ArtistRankingItemResponse.toModel(): RankedArtist =
    RankedArtist(
        artist = Artist(
            id = artistSeq,
            name = name,
            profileImageUrl = profileImageUrl.orEmpty(),
            groupName = groupName,
        ),
        rank = rank,
        rankDelta = rankDelta,
        isSubscribed = isSubscribed,
    )

internal fun ArtistSearchResponse.toModel(): List<ArtistWithSubscription> =
    list.map { response ->
        response.toModel()
    }

internal fun ArtistListResponse.toModel(): List<ArtistWithSubscription> =
    list.map { response ->
        response.toModel()
    }

private fun ArtistSubscriptionSummaryResponse.toModel():
        ArtistWithSubscription =
    ArtistWithSubscription(
        artist = Artist(
            id = artistSeq,
            name = name,
            profileImageUrl = profileImageUrl.orEmpty(),
            groupName = groupName,
        ),
        isSubscribed = isSubscribed,
    )

internal fun ArtistDetailResponse.toModel(): ArtistDetail =
    ArtistDetail(
        artist = Artist(
            id = artistSeq,
            name = name,
            profileImageUrl = profileImageUrl.orEmpty(),
            groupName = groupName,
        ),
        introduction = bio,
        subscriberCount = subscriberCount,
        challengeCount = challengeCount,
        isSubscribed = isSubscribed,
        subscribedDays = subscribedDays,
        motifPass = pass.toModel(),
        myMotifVideos = myMotifVideos.map { it.toModel() },
    )

private fun ArtistPassResponse.toModel(): ArtistPass =
    ArtistPass(
        active = active,
        expiresAt = expiresDttm?.toEpochMilliOrNull(),
    )

private fun ArtistMotifVideoResponse.toModel(): ArtistMotifVideo =
    ArtistMotifVideo(
        id = resultSeq,
        videoUrl = videoUrl,
        thumbnailUrl = thumbnailUrl,
        durationMs = durationMs,
    )

internal fun ArtistSubscriptionResponse.toModel(): ArtistSubscriptionResult =
    ArtistSubscriptionResult(
        isSubscribed = isSubscribed,
        subscriberCount = subscriberCount,
    )

internal fun ArtistFilter.toNetwork(): ArtistRankingFilter =
    when (this) {
        ArtistFilter.ALL ->
            ArtistRankingFilter.ALL

        ArtistFilter.SUBSCRIBED ->
            ArtistRankingFilter.SUBSCRIBED

        ArtistFilter.UNSUBSCRIBED ->
            ArtistRankingFilter.UNSUBSCRIBED
    }

private fun String.toEpochMilliOrNull(): Long? =
    runCatching {
        Instant.parse(this).toEpochMilli()
    }.getOrNull()