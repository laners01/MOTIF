package com.ssafy.motif.core.network.service.artist

import com.ssafy.motif.core.network.model.BaseResponse
import com.ssafy.motif.core.network.model.artist.ArtistChallengeListResponse
import com.ssafy.motif.core.network.model.artist.ArtistDetailResponse
import com.ssafy.motif.core.network.model.artist.ArtistListResponse
import com.ssafy.motif.core.network.model.artist.ArtistNoticeListResponse
import com.ssafy.motif.core.network.model.artist.ArtistRankingFilter
import com.ssafy.motif.core.network.model.artist.ArtistRankingResponse
import com.ssafy.motif.core.network.model.artist.ArtistSearchResponse
import com.ssafy.motif.core.network.model.artist.ArtistSubscriptionResponse
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ArtistService {

    @GET("/api/v1/artists/ranking")
    suspend fun getArtistRanking(
        @Query("filter") filter: ArtistRankingFilter,
        @Query("page") page: Int,
        @Query("size") size: Int,
    ): BaseResponse<ArtistRankingResponse>

    @GET("/api/v1/artists/search")
    suspend fun searchArtists(
        @Query("q") query: String,
        @Query("size") size: Int,
    ): BaseResponse<ArtistSearchResponse>

    @GET("/api/v1/artists/{artistSeq}")
    suspend fun getArtistDetail(
        @Path("artistSeq") artistSeq: Long,
    ): BaseResponse<ArtistDetailResponse>

    @GET("/api/v1/artists/{artistSeq}/challenges")
    suspend fun getArtistChallenges(
        @Path("artistSeq") artistSeq: Long,
    ): BaseResponse<ArtistChallengeListResponse>

    @GET("/api/v1/artists/{artistSeq}/notices")
    suspend fun getArtistNotices(
        @Path("artistSeq") artistSeq: Long,
        @Query("cursor") cursor: String?,
        @Query("size") size: Int,
    ): BaseResponse<ArtistNoticeListResponse>

    @POST("/api/v1/artists/{artistSeq}/subscription")
    suspend fun subscribeArtist(
        @Path("artistSeq") artistSeq: Long,
    ): BaseResponse<ArtistSubscriptionResponse>

    @DELETE("/api/v1/artists/{artistSeq}/subscription")
    suspend fun unsubscribeArtist(
        @Path("artistSeq") artistSeq: Long,
    ): BaseResponse<ArtistSubscriptionResponse>

    @GET("/api/v1/artists")
    suspend fun getArtists(): BaseResponse<ArtistListResponse>
}