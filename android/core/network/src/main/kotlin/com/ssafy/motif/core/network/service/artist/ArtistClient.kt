package com.ssafy.motif.core.network.service.artist

import com.ssafy.motif.core.network.model.artist.ArtistChallengeListResponse
import com.ssafy.motif.core.network.model.artist.ArtistDetailResponse
import com.ssafy.motif.core.network.model.artist.ArtistFeedResponse
import com.ssafy.motif.core.network.model.artist.ArtistListResponse
import com.ssafy.motif.core.network.model.artist.ArtistNoticeListResponse
import com.ssafy.motif.core.network.model.artist.ArtistRankingFilter
import com.ssafy.motif.core.network.model.artist.ArtistRankingResponse
import com.ssafy.motif.core.network.model.artist.ArtistSearchResponse
import com.ssafy.motif.core.network.model.artist.ArtistSubscriptionResponse
import javax.inject.Inject

class ArtistClient @Inject constructor(
    private val artistService: ArtistService,
) {

    suspend fun getArtistRanking(
        filter: ArtistRankingFilter = ArtistRankingFilter.ALL,
        page: Int = DEFAULT_PAGE,
        size: Int = DEFAULT_PAGE_SIZE,
    ): ArtistRankingResponse =
        artistService.getArtistRanking(
            filter = filter,
            page = page,
            size = size,
        ).data

    suspend fun searchArtists(
        query: String,
        size: Int = DEFAULT_PAGE_SIZE,
    ): ArtistSearchResponse =
        artistService.searchArtists(
            query = query,
            size = size,
        ).data

    suspend fun getArtistDetail(
        id: Long,
    ): ArtistDetailResponse =
        artistService.getArtistDetail(
            artistSeq = id,
        ).data

    suspend fun getArtistChallenges(
        id: Long,
    ): ArtistChallengeListResponse =
        artistService.getArtistChallenges(
            artistSeq = id,
        ).data

    suspend fun getArtistNotices(
        id: Long,
        cursor: String? = null,
        size: Int = DEFAULT_PAGE_SIZE,
    ): ArtistNoticeListResponse =
        artistService.getArtistNotices(
            artistSeq = id,
            cursor = cursor,
            size = size,
        ).data

    suspend fun updateArtistSubscription(
        id: Long,
        isSubscribed: Boolean,
    ): ArtistSubscriptionResponse =
        if (isSubscribed) {
            artistService.subscribeArtist(
                artistSeq = id,
            ).data
        } else {
            artistService.unsubscribeArtist(
                artistSeq = id,
            ).data
        }

    suspend fun getArtists(): ArtistListResponse =
        artistService.getArtists().data

    suspend fun getArtistFeed(
        page: Int = DEFAULT_PAGE,
        size: Int = DEFAULT_PAGE_SIZE,
    ): ArtistFeedResponse =
        artistService.getArtistFeed(
            page = page,
            size = size,
        ).data

    private companion object {
        const val DEFAULT_PAGE = 0
        const val DEFAULT_PAGE_SIZE = 20
    }
}