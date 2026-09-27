package com.ssafy.motif.core.data.repository

import com.ssafy.motif.core.data.model.CursorPage
import com.ssafy.motif.core.data.model.OffsetPage
import com.ssafy.motif.core.model.artist.ArtistDetail
import com.ssafy.motif.core.model.artist.ArtistFeedItem
import com.ssafy.motif.core.model.artist.ArtistFilter
import com.ssafy.motif.core.model.artist.ArtistSubscriptionResult
import com.ssafy.motif.core.model.artist.ArtistWithSubscription
import com.ssafy.motif.core.model.challenge.ChallengeRank
import com.ssafy.motif.core.model.challenge.ChallengeSummary
import com.ssafy.motif.core.model.notice.Notice
import kotlinx.coroutines.flow.Flow

interface ArtistRepository {

    fun getArtistRanking(
        filter: ArtistFilter = ArtistFilter.ALL,
        page: Int = DEFAULT_PAGE,
        size: Int = DEFAULT_PAGE_SIZE,
    ): Flow<OffsetPage<ChallengeRank>>

    fun searchArtists(
        query: String,
        size: Int = DEFAULT_PAGE_SIZE,
    ): Flow<List<ArtistWithSubscription>>

    fun getArtistDetail(
        id: Long,
    ): Flow<ArtistDetail>

    fun getArtistChallenges(
        id: Long,
    ): Flow<List<ChallengeSummary>>

    fun getArtistNotices(
        id: Long,
        cursor: String? = null,
        size: Int = DEFAULT_PAGE_SIZE,
    ): Flow<CursorPage<Notice>>

    fun updateArtistSubscription(
        id: Long,
        isSubscribed: Boolean,
    ): Flow<ArtistSubscriptionResult>

    fun getArtists(): Flow<List<ArtistWithSubscription>>

    fun getArtistFeed(
        page: Int = DEFAULT_PAGE,
        size: Int = DEFAULT_PAGE_SIZE,
    ): Flow<OffsetPage<ArtistFeedItem>>

    private companion object {
        const val DEFAULT_PAGE = 0
        const val DEFAULT_PAGE_SIZE = 20
    }
}