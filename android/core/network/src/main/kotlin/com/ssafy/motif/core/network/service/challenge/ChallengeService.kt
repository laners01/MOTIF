package com.ssafy.motif.core.network.service.challenge

import com.ssafy.motif.core.network.model.BaseResponse
import com.ssafy.motif.core.network.model.challenge.ChallengeDetailResponse
import com.ssafy.motif.core.network.model.challenge.ChallengeFeedResponse
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ChallengeService {

    @POST("/api/v1/challenges/{challengeSeq}/like")
    suspend fun postChallengeLike(
        @Path("challengeSeq") challengeSeq: Long,
    )

    @DELETE("/api/v1/challenges/{challengeSeq}/like")
    suspend fun deleteChallengeLike(
        @Path("challengeSeq") challengeSeq: Long,
    )

    @GET("/api/v1/challenges/{challengeSeq}")
    suspend fun getChallengeDetail(
        @Path("challengeSeq") challengeSeq: Long,
    ): BaseResponse<ChallengeDetailResponse>

    @GET("/api/v1/challenges/feed")
    suspend fun getChallengeFeed(
        @Query("cursor") cursor: String? = null,
        @Query("size") size: Int = DEFAULT_FEED_SIZE,
    ): BaseResponse<ChallengeFeedResponse>

    companion object {
        private const val DEFAULT_FEED_SIZE = 20
    }
}