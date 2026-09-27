package com.ssafy.motif.core.data.repository

import com.ssafy.motif.core.common.network.Dispatcher
import com.ssafy.motif.core.common.network.MotifDispatcher
import com.ssafy.motif.core.data.mapper.toModel
import com.ssafy.motif.core.data.model.CursorPage
import com.ssafy.motif.core.model.composite.CompositeJobRequestResult
import com.ssafy.motif.core.model.composite.CompositeJobUpload
import com.ssafy.motif.core.model.motif.Asset
import com.ssafy.motif.core.model.motif.SavedMotif
import com.ssafy.motif.core.model.shop.ArtistPass
import com.ssafy.motif.core.model.shop.MyMotifPass
import com.ssafy.motif.core.model.shop.PassBanner
import com.ssafy.motif.core.model.shop.ShopArtistSection
import com.ssafy.motif.core.network.service.composite.CompositeJobClient
import com.ssafy.motif.core.network.service.motif.MotifClient
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

internal class MotifRepositoryImpl @Inject constructor(
    private val motifClient: MotifClient,
    private val compositeJobClient: CompositeJobClient,
    @param:Dispatcher(MotifDispatcher.IO)
    private val ioDispatcher: CoroutineDispatcher,
) : MotifRepository {

    override fun getPurchasedAssets(
        artistId: Long?,
    ): Flow<List<Asset>> =
        flow {
            val response =
                motifClient.getPurchasedAssets(
                    artistId = artistId,
                )

            emit(
                response.toModel(),
            )
        }.flowOn(ioDispatcher)

    override fun getAssetDetail(
        assetId: Long,
    ): Flow<Asset> =
        flow {
            val response =
                motifClient.getAssetDetail(
                    assetId = assetId,
                )

            emit(
                response.toModel(),
            )
        }.flowOn(ioDispatcher)

    override fun getShop():
            Flow<List<ShopArtistSection>> =
        flow {
            val response =
                motifClient.getShop()

            emit(
                response.toModel(),
            )
        }.flowOn(ioDispatcher)

    override fun getArtistMotifPass(
        artistId: Long,
    ): Flow<ArtistPass> =
        flow {
            val response =
                motifClient.getArtistMotifPass(
                    artistId = artistId,
                )

            emit(
                response.toModel(),
            )
        }.flowOn(ioDispatcher)

    override fun getMotifResults(
        cursor: String?,
        size: Int,
        artistId: Long?,
    ): Flow<CursorPage<SavedMotif>> =
        flow {
            val response =
                motifClient.getMotifResults(
                    cursor = cursor,
                    size = size,
                    artistId = artistId,
                )

            emit(
                response.toModel(),
            )
        }.flowOn(ioDispatcher)

    override fun getMotifResultDetail(
        resultId: Long,
    ): Flow<SavedMotif> =
        flow {
            val response =
                motifClient.getMotifResultDetail(
                    resultId = resultId,
                )

            emit(
                response.toModel(),
            )
        }.flowOn(ioDispatcher)

    override fun updateMotifResult(
        resultId: Long,
        description: String?,
        isRepresentative: Boolean?,
    ): Flow<SavedMotif> =
        flow {
            val response =
                motifClient.updateMotifResult(
                    resultId = resultId,
                    description = description,
                    isRepresentative = isRepresentative,
                )

            emit(
                response.toModel(),
            )
        }.flowOn(ioDispatcher)

    override fun deleteMotifResult(
        resultId: Long,
    ): Flow<Unit> =
        flow {
            motifClient.deleteMotifResult(
                resultId = resultId,
            )

            emit(Unit)
        }.flowOn(ioDispatcher)

    override fun getMyMotifPasses():
            Flow<List<MyMotifPass>> =
        flow {
            val response =
                motifClient.getMyMotifPasses()

            emit(
                response.toModel(),
            )
        }.flowOn(ioDispatcher)

    override fun getShopBanners():
            Flow<List<PassBanner>> =
        flow {
            val response =
                motifClient.getShopBanners()

            emit(
                response.toModel(),
            )
        }.flowOn(ioDispatcher)

    override fun createMotifCompositeJob(
        assetId: Long,
        contentLength: Long,
    ): Flow<CompositeJobUpload> =
        flow {
            val response =
                compositeJobClient.createMotifJob(
                    assetId = assetId,
                    contentLength = contentLength,
                )

            emit(
                response.toModel(),
            )
        }.flowOn(ioDispatcher)

    override fun uploadMotifCompositeVideo(
        uploadUrl: String,
        videoFile: File,
    ): Flow<Int> =
        channelFlow {
            trySend(INITIAL_UPLOAD_PROGRESS)

            compositeJobClient.uploadVideo(
                uploadUrl = uploadUrl,
                videoFile = videoFile,
                onProgress = { progress ->
                    trySend(progress)
                },
            )
        }
            .distinctUntilChanged()
            .flowOn(ioDispatcher)

    override fun requestMotifCompositeJob(
        jobUuid: String,
    ): Flow<CompositeJobRequestResult> =
        flow {
            val response =
                compositeJobClient.requestMotifJob(
                    jobUuid = jobUuid,
                )

            emit(
                response.toModel(),
            )
        }.flowOn(ioDispatcher)

    private companion object {
        const val INITIAL_UPLOAD_PROGRESS = 0
    }
}
