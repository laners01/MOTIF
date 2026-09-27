package com.ssafy.motif.core.data.repository

import com.ssafy.motif.core.common.network.Dispatcher
import com.ssafy.motif.core.common.network.MotifDispatcher
import com.ssafy.motif.core.data.mapper.toModel
import com.ssafy.motif.core.data.mapper.toNetwork
import com.ssafy.motif.core.data.model.CursorPage
import com.ssafy.motif.core.data.model.OffsetPage
import com.ssafy.motif.core.model.artist.ArtistDetail
import com.ssafy.motif.core.model.artist.ArtistFeedItem
import com.ssafy.motif.core.model.artist.ArtistFilter
import com.ssafy.motif.core.model.artist.ArtistSubscriptionResult
import com.ssafy.motif.core.model.artist.ArtistWithSubscription
import com.ssafy.motif.core.model.artist.RankedArtist
import com.ssafy.motif.core.model.challenge.ChallengeSummary
import com.ssafy.motif.core.model.notice.Notice
import com.ssafy.motif.core.network.service.artist.ArtistClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

internal class ArtistRepositoryImpl @Inject constructor(
    private val artistClient: ArtistClient,
    @param:Dispatcher(MotifDispatcher.IO)
    private val ioDispatcher: CoroutineDispatcher,
) : ArtistRepository {

    override fun getArtistRanking(
        filter: ArtistFilter,
        page: Int,
        size: Int,
    ): Flow<OffsetPage<RankedArtist>> =
        flow {
            val response = artistClient.getArtistRanking(
                filter = filter.toNetwork(),
                page = page,
                size = size,
            )

            emit(response.toModel())
        }.flowOn(ioDispatcher)

    override fun searchArtists(
        query: String,
        size: Int,
    ): Flow<List<ArtistWithSubscription>> =
        flow {
            val response = artistClient.searchArtists(
                query = query,
                size = size,
            )

            emit(response.toModel())
        }.flowOn(ioDispatcher)

    override fun getArtistDetail(
        id: Long,
    ): Flow<ArtistDetail> =
        flow {
            val response = artistClient.getArtistDetail(
                id = id,
            )

            emit(response.toModel())
        }.flowOn(ioDispatcher)

    override fun getArtistChallenges(
        id: Long,
    ): Flow<List<ChallengeSummary>> =
        flow {
            val response = artistClient.getArtistChallenges(
                id = id,
            )

            emit(response.toModel())
        }.flowOn(ioDispatcher)

    override fun getArtistNotices(
        id: Long,
        cursor: String?,
        size: Int,
    ): Flow<CursorPage<Notice>> =
        flow {
            val response = artistClient.getArtistNotices(
                id = id,
                cursor = cursor,
                size = size,
            )

            emit(response.toModel())
        }.flowOn(ioDispatcher)

    override fun updateArtistSubscription(
        id: Long,
        isSubscribed: Boolean,
    ): Flow<ArtistSubscriptionResult> =
        flow {
            val response = artistClient.updateArtistSubscription(
                id = id,
                isSubscribed = isSubscribed,
            )

            emit(response.toModel())
        }.flowOn(ioDispatcher)

    override fun getArtists(): Flow<List<ArtistWithSubscription>> =
        flow {
            val response = artistClient.getArtists()

            emit(response.toModel())
        }.flowOn(ioDispatcher)

    override fun getArtistFeed(
        page: Int,
        size: Int,
    ): Flow<OffsetPage<ArtistFeedItem>> =
        flow {
            val response = artistClient.getArtistFeed(
                page = page,
                size = size,
            )

            emit(response.toModel())
        }.flowOn(ioDispatcher)
}