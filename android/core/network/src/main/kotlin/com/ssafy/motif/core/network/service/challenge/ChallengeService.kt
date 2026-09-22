package com.ssafy.motif.core.network.service.challenge

import com.ssafy.motif.core.network.model.BaseResponse
import com.ssafy.motif.core.network.model.challenge.ChallengeDetailResponse
import com.ssafy.motif.core.network.model.challenge.ChallengeFeedResponse
import com.ssafy.motif.core.network.model.challenge.HotChallengeParticipationFeedResponse
import com.ssafy.motif.core.network.model.challenge.ParticipationVideoFeedResponse
import com.ssafy.motif.core.network.model.challenge.ParticipationVideoResponse
import com.ssafy.motif.core.network.model.challenge.PublishParticipationVideoRequest
import com.ssafy.motif.core.network.model.challenge.PublishParticipationVideoResponse
import com.ssafy.motif.core.network.model.challenge.ReportParticipationVideoRequest
import com.ssafy.motif.core.network.model.challenge.UpdateParticipationVideoRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ChallengeService {

    @GET("/api/v1/challenges/feed")
    suspend fun getChallengeFeed(
        @Query("cursor") cursor: String?,
        @Query("size") size: Int,
    ): BaseResponse<ChallengeFeedResponse>

    @GET("/api/v1/challenges/{challengeSeq}")
    suspend fun getChallengeDetail(
        @Path("challengeSeq") challengeSeq: Long,
    ): BaseResponse<ChallengeDetailResponse>

    @POST("/api/v1/challenges/{challengeSeq}/like")
    suspend fun postChallengeLike(
        @Path("challengeSeq") challengeSeq: Long,
    )

    @DELETE("/api/v1/challenges/{challengeSeq}/like")
    suspend fun deleteChallengeLike(
        @Path("challengeSeq") challengeSeq: Long,
    )

    @GET("/api/v1/challenges/{challengeSeq}/videos")
    suspend fun getParticipationVideoFeed(
        @Path("challengeSeq") challengeSeq: Long,
        @Query("cursor") cursor: String?,
        @Query("size") size: Int,
    ): BaseResponse<ParticipationVideoFeedResponse>

    @GET("/api/v1/videos/{videoSeq}")
    suspend fun getParticipationVideo(
        @Path("videoSeq") videoSeq: Long,
    ): BaseResponse<ParticipationVideoResponse>

    @POST("/api/v1/videos")
    suspend fun publishParticipationVideo(
        @Body request: PublishParticipationVideoRequest,
    ): BaseResponse<PublishParticipationVideoResponse>

    @PATCH("/api/v1/videos/{videoSeq}")
    suspend fun updateParticipationVideo(
        @Path("videoSeq") videoSeq: Long,
        @Body request: UpdateParticipationVideoRequest,
    ): BaseResponse<ParticipationVideoResponse>

    @DELETE("/api/v1/videos/{videoSeq}")
    suspend fun deleteParticipationVideo(
        @Path("videoSeq") videoSeq: Long,
    ): BaseResponse<Unit?>

    @POST("/api/v1/videos/{videoSeq}/like")
    suspend fun postParticipationVideoLike(
        @Path("videoSeq") videoSeq: Long,
    )

    @DELETE("/api/v1/videos/{videoSeq}/like")
    suspend fun deleteParticipationVideoLike(
        @Path("videoSeq") videoSeq: Long,
    )

    @POST("/api/v1/videos/{videoSeq}/hide")
    suspend fun hideParticipationVideo(
        @Path("videoSeq") videoSeq: Long,
    ): BaseResponse<Unit?>

    @POST("/api/v1/videos/{videoSeq}/report")
    suspend fun reportParticipationVideo(
        @Path("videoSeq") videoSeq: Long,
        @Body request: ReportParticipationVideoRequest,
    ): BaseResponse<Unit?>

    @GET("/api/v1/home/hot-videos")
    suspend fun getHotChallengeParticipations(
        @Query("cursor") cursor: String?,
        @Query("size") size: Int,
    ): BaseResponse<HotChallengeParticipationFeedResponse>
}