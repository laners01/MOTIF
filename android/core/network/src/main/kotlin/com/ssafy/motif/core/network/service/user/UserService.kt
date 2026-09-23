package com.ssafy.motif.core.network.service.user

import com.ssafy.motif.core.network.model.BaseResponse
import com.ssafy.motif.core.network.model.user.ProfileImagePresignRequest
import com.ssafy.motif.core.network.model.user.ProfileImagePresignResponse
import com.ssafy.motif.core.network.model.user.UpdateNicknameRequest
import com.ssafy.motif.core.network.model.user.UpdateNicknameResponse
import com.ssafy.motif.core.network.model.user.UpdateProfileImageRequest
import com.ssafy.motif.core.network.model.user.UpdateProfileImageResponse
import com.ssafy.motif.core.network.model.user.UserActivityListResponse
import com.ssafy.motif.core.network.model.user.UserOverviewResponse
import com.ssafy.motif.core.network.model.user.UserProfileResponse
import com.ssafy.motif.core.network.model.user.UserSubscriptionListResponse
import com.ssafy.motif.core.network.model.user.UserVideoListResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface UserService {

    @GET("/api/v1/users/me")
    suspend fun getMe(): BaseResponse<UserOverviewResponse>

    @PATCH("/api/v1/users/me/nickname")
    suspend fun updateNickname(
        @Body request: UpdateNicknameRequest,
    ): BaseResponse<UpdateNicknameResponse>

    @POST("/api/v1/users/me/profile-image/presign")
    suspend fun createProfileImagePresign(
        @Body request: ProfileImagePresignRequest,
    ): BaseResponse<ProfileImagePresignResponse>

    @PATCH("/api/v1/users/me/profile-image")
    suspend fun updateProfileImage(
        @Body request: UpdateProfileImageRequest,
    ): BaseResponse<UpdateProfileImageResponse>

    @GET("/api/v1/users/{userSeq}/profile")
    suspend fun getUserProfile(
        @Path("userSeq") userSeq: Long,
    ): BaseResponse<UserProfileResponse>

    @GET("/api/v1/users/me/subscriptions")
    suspend fun getSubscriptions(): BaseResponse<UserSubscriptionListResponse>

    @GET("/api/v1/users/me/videos")
    suspend fun getVideos(): BaseResponse<UserVideoListResponse>

    @GET("/api/v1/users/me/activities")
    suspend fun getActivities(
        @Query("cursor") cursor: String?,
        @Query("size") size: Int,
    ): BaseResponse<UserActivityListResponse>
}