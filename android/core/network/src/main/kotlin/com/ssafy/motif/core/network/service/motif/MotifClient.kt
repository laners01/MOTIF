package com.ssafy.motif.core.network.service.motif

import com.ssafy.motif.core.network.model.motif.ArtistMotifPassResponse
import com.ssafy.motif.core.network.model.motif.MotifAssetDetailResponse
import com.ssafy.motif.core.network.model.motif.MotifAssetListResponse
import com.ssafy.motif.core.network.model.motif.MotifResultDetailResponse
import com.ssafy.motif.core.network.model.motif.MotifResultListResponse
import com.ssafy.motif.core.network.model.motif.MotifShopResponse
import com.ssafy.motif.core.network.model.motif.MyMotifPassListResponse
import com.ssafy.motif.core.network.model.motif.ShopBannerListResponse
import com.ssafy.motif.core.network.model.motif.UpdateMotifResultRequest
import javax.inject.Inject

class MotifClient @Inject constructor(
    private val motifService: MotifService,
) {

    suspend fun getPurchasedAssets(
        artistId: Long? = null,
    ): MotifAssetListResponse =
        motifService
            .getPurchasedAssets(
                artistSeq = artistId,
            )
            .data

    suspend fun getAssetDetail(
        assetId: Long,
    ): MotifAssetDetailResponse =
        motifService
            .getAssetDetail(
                assetSeq = assetId,
            )
            .data

    suspend fun getShop(): MotifShopResponse =
        motifService
            .getShop()
            .data

    suspend fun getArtistMotifPass(
        artistId: Long,
    ): ArtistMotifPassResponse =
        motifService
            .getArtistMotifPass(
                artistSeq = artistId,
            )
            .data

    suspend fun getMotifResults(
        cursor: String? = null,
        size: Int = DEFAULT_RESULT_SIZE,
        artistId: Long? = null,
    ): MotifResultListResponse =
        motifService
            .getMotifResults(
                cursor = cursor,
                size = size,
                artistSeq = artistId,
            )
            .data

    suspend fun getMotifResultDetail(
        resultId: Long,
    ): MotifResultDetailResponse =
        motifService
            .getMotifResultDetail(
                resultSeq = resultId,
            )
            .data

    suspend fun updateMotifResult(
        resultId: Long,
        description: String? = null,
        isRepresentative: Boolean? = null,
    ): MotifResultDetailResponse =
        motifService
            .updateMotifResult(
                resultSeq = resultId,
                request = UpdateMotifResultRequest(
                    description = description,
                    isRep = isRepresentative,
                ),
            )
            .data

    suspend fun deleteMotifResult(
        resultId: Long,
    ) {
        motifService.deleteMotifResult(
            resultSeq = resultId,
        )
    }

    suspend fun getMyMotifPasses():
            MyMotifPassListResponse =
        motifService
            .getMyMotifPasses()
            .data

    suspend fun getShopBanners():
            ShopBannerListResponse =
        motifService
            .getShopBanners()
            .data

    private companion object {
        const val DEFAULT_RESULT_SIZE = 20
    }
}