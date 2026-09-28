package com.ssafy.motif.core.data.repository

import com.ssafy.motif.core.data.model.CursorPage
import com.ssafy.motif.core.model.composite.CompositeJobRequestResult
import com.ssafy.motif.core.model.composite.CompositeJobUpload
import com.ssafy.motif.core.model.motif.Asset
import com.ssafy.motif.core.model.motif.SavedMotif
import com.ssafy.motif.core.model.shop.ArtistPass
import com.ssafy.motif.core.model.shop.MyMotifPass
import com.ssafy.motif.core.model.shop.PassBanner
import com.ssafy.motif.core.model.shop.ShopArtistSection
import java.io.File
import kotlinx.coroutines.flow.Flow

interface MotifRepository {

    fun getPurchasedAssets(
        artistId: Long? = null,
    ): Flow<List<Asset>>

    fun getAssetDetail(
        assetId: Long,
    ): Flow<Asset>

    fun getShop():
            Flow<List<ShopArtistSection>>

    fun getArtistMotifPass(
        artistId: Long,
    ): Flow<ArtistPass>

    fun getMotifResults(
        cursor: String? = null,
        size: Int = DEFAULT_RESULT_SIZE,
        artistId: Long? = null,
    ): Flow<CursorPage<SavedMotif>>

    fun getMotifResultDetail(
        resultId: Long,
    ): Flow<SavedMotif>

    fun updateMotifResult(
        resultId: Long,
        description: String? = null,
        isRepresentative: Boolean? = null,
    ): Flow<SavedMotif>

    fun deleteMotifResult(
        resultId: Long,
    ): Flow<Unit>

    fun getMyMotifPasses():
            Flow<List<MyMotifPass>>

    fun getShopBanners():
            Flow<List<PassBanner>>

    fun createMotifCompositeJob(
        assetId: Long,
        contentLength: Long,
    ): Flow<CompositeJobUpload>

    fun uploadMotifCompositeVideo(
        uploadUrl: String,
        videoFile: File,
    ): Flow<Int>

    fun requestMotifCompositeJob(
        jobUuid: String,
    ): Flow<CompositeJobRequestResult>

    private companion object {
        const val DEFAULT_RESULT_SIZE = 20
    }
}
