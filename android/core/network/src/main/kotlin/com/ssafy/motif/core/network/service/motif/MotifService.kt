package com.ssafy.motif.core.network.service.motif

import com.ssafy.motif.core.network.model.BaseResponse
import com.ssafy.motif.core.network.model.motif.ArtistMotifPassResponse
import com.ssafy.motif.core.network.model.motif.MotifAssetDetailResponse
import com.ssafy.motif.core.network.model.motif.MotifAssetListResponse
import com.ssafy.motif.core.network.model.motif.MotifResultDetailResponse
import com.ssafy.motif.core.network.model.motif.MotifResultListResponse
import com.ssafy.motif.core.network.model.motif.MotifShopResponse
import com.ssafy.motif.core.network.model.motif.MyMotifPassListResponse
import com.ssafy.motif.core.network.model.motif.ShopBannerListResponse
import com.ssafy.motif.core.network.model.motif.UpdateMotifResultRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.Query

interface MotifService {

    @GET("/api/v1/motif/assets")
    suspend fun getPurchasedAssets(
        @Query("artistSeq")
        artistSeq: Long?,
    ): BaseResponse<MotifAssetListResponse>

    @GET("/api/v1/motif/assets/{assetSeq}")
    suspend fun getAssetDetail(
        @Path("assetSeq")
        assetSeq: Long,
    ): BaseResponse<MotifAssetDetailResponse>

    @GET("/api/v1/motif/shop")
    suspend fun getShop(): BaseResponse<MotifShopResponse>

    @GET("/api/v1/artists/{artistSeq}/motif-pass")
    suspend fun getArtistMotifPass(
        @Path("artistSeq")
        artistSeq: Long,
    ): BaseResponse<ArtistMotifPassResponse>

    @GET("/api/v1/motif/results")
    suspend fun getMotifResults(
        @Query("cursor")
        cursor: String?,
        @Query("size")
        size: Int,
        @Query("artistSeq")
        artistSeq: Long?,
    ): BaseResponse<MotifResultListResponse>

    @GET("/api/v1/motif/results/{resultSeq}")
    suspend fun getMotifResultDetail(
        @Path("resultSeq")
        resultSeq: Long,
    ): BaseResponse<MotifResultDetailResponse>

    @PATCH("/api/v1/motif/results/{resultSeq}")
    suspend fun updateMotifResult(
        @Path("resultSeq")
        resultSeq: Long,
        @Body
        request: UpdateMotifResultRequest,
    ): BaseResponse<MotifResultDetailResponse>

    @DELETE("/api/v1/motif/results/{resultSeq}")
    suspend fun deleteMotifResult(
        @Path("resultSeq")
        resultSeq: Long,
    ): BaseResponse<Unit?>

    @GET("/api/v1/users/me/motif-passes")
    suspend fun getMyMotifPasses():
            BaseResponse<MyMotifPassListResponse>

    @GET("/api/v1/shop/banners")
    suspend fun getShopBanners():
            BaseResponse<ShopBannerListResponse>
}