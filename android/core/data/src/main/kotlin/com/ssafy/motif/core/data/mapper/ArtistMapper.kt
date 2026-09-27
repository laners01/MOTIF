package com.ssafy.motif.core.data.mapper

import com.ssafy.motif.core.data.model.CursorPage
import com.ssafy.motif.core.data.model.OffsetPage
import com.ssafy.motif.core.model.artist.Artist
import com.ssafy.motif.core.model.artist.ArtistDetail
import com.ssafy.motif.core.model.artist.ArtistFeedItem
import com.ssafy.motif.core.model.artist.ArtistFilter
import com.ssafy.motif.core.model.artist.ArtistMotifVideo
import com.ssafy.motif.core.model.artist.ArtistPass
import com.ssafy.motif.core.model.artist.ArtistSubscriptionResult
import com.ssafy.motif.core.model.artist.ArtistWithSubscription
import com.ssafy.motif.core.model.artist.RankedArtist
import com.ssafy.motif.core.model.challenge.ChallengeSummary
import com.ssafy.motif.core.model.motif.Motif
import com.ssafy.motif.core.model.notice.Notice
import com.ssafy.motif.core.network.model.artist.ArtistChallengeListResponse
import com.ssafy.motif.core.network.model.artist.ArtistChallengeResponse
import com.ssafy.motif.core.network.model.artist.ArtistDetailResponse
import com.ssafy.motif.core.network.model.artist.ArtistFeedArtistResponse
import com.ssafy.motif.core.network.model.artist.ArtistFeedItemResponse
import com.ssafy.motif.core.network.model.artist.ArtistFeedResponse
import com.ssafy.motif.core.network.model.artist.ArtistFeedType
import com.ssafy.motif.core.network.model.artist.ArtistListResponse
import com.ssafy.motif.core.network.model.artist.ArtistMotifVideoResponse
import com.ssafy.motif.core.network.model.artist.ArtistNoticeListResponse
import com.ssafy.motif.core.network.model.artist.ArtistNoticeResponse
import com.ssafy.motif.core.network.model.artist.ArtistPassResponse
import com.ssafy.motif.core.network.model.artist.ArtistRankingFilter
import com.ssafy.motif.core.network.model.artist.ArtistRankingItemResponse
import com.ssafy.motif.core.network.model.artist.ArtistRankingResponse
import com.ssafy.motif.core.network.model.artist.ArtistSearchResponse
import com.ssafy.motif.core.network.model.artist.ArtistSubscriptionResponse
import com.ssafy.motif.core.network.model.artist.ArtistSubscriptionSummaryResponse
import com.ssafy.motif.core.network.model.artist.ArtistSummaryResponse
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
        myMotifVideos = myMotifVideos.map { response ->
            response.toModel()
        },
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

internal fun ArtistChallengeListResponse.toModel(): List<ChallengeSummary> {
    val now = System.currentTimeMillis()

    return list.map { response ->
        response.toModel(
            createdAt = now,
        )
    }
}

private fun ArtistChallengeResponse.toModel(
    createdAt: Long,
): ChallengeSummary =
    ChallengeSummary(
        id = challengeSeq,
        thumbnailUrl = thumbnailUrl,
        view = viewCount,
        createdAt = createdAt,
    )

internal fun ArtistNoticeListResponse.toModel(): CursorPage<Notice> =
    CursorPage(
        items = list.map { response ->
            response.toModel()
        },
        previousCursor = prevCursor,
        nextCursor = nextCursor,
        hasPrevious = hasPrevious,
        hasNext = hasNext,
    )

private fun ArtistNoticeResponse.toModel(): Notice =
    Notice(
        id = noticeSeq,
        artist = artist.toModel(),
        content = content,
        imageUrls = images
            .sortedBy { image ->
                image.displayOrder
            }
            .map { image ->
                image.imageUrl
            },
        likeCount = likeCount,
        commentCount = commentCount,
        isLiked = isLiked,
        createdAt = regDttm.toEpochMilli(),
    )

private fun ArtistSummaryResponse.toModel(): Artist =
    Artist(
        id = artistSeq,
        name = name,
        profileImageUrl = profileImageUrl.orEmpty(),
        groupName = groupName,
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

internal fun ArtistFeedResponse.toModel(): OffsetPage<ArtistFeedItem> =
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

private fun ArtistFeedItemResponse.toModel(): ArtistFeedItem {
    val createdAt = regDttm.toEpochMilli()

    return when (type) {
        ArtistFeedType.MOTIF_ASSET -> {
            val asset = requireNotNull(asset) {
                "MOTIF_ASSET feed item must contain asset."
            }

            ArtistFeedItem.MotifAsset(
                motif = Motif(
                    id = asset.assetSeq,
                    thumbnailUrl = asset.thumbnailUrl,
                    title = asset.title,
                    artist = artist.toModel(),
                    createdAt = createdAt,
                    description = "",
                    videoUrl = "",
                ),
                durationMs = asset.durationMs,
            )
        }

        ArtistFeedType.NOTICE -> {
            val notice = requireNotNull(notice) {
                "NOTICE feed item must contain notice."
            }

            ArtistFeedItem.NoticeItem(
                notice = Notice(
                    id = notice.noticeSeq,
                    artist = artist.toModel(),
                    content = notice.content,
                    imageUrls = notice.images
                        .sortedBy { image ->
                            image.displayOrder
                        }
                        .map { image ->
                            image.imageUrl
                        },
                    likeCount = notice.likeCount,
                    commentCount = notice.commentCount,
                    isLiked = notice.isLiked,
                    createdAt = createdAt,
                ),
            )
        }
    }
}

private fun ArtistFeedArtistResponse.toModel(): Artist =
    Artist(
        id = artistSeq,
        name = name,
        profileImageUrl = profileImageUrl.orEmpty(),
        groupName = groupName,
    )

private fun String.toEpochMilli(): Long =
    runCatching {
        Instant.parse(this).toEpochMilli()
    }.getOrDefault(0L)