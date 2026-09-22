package com.ssafy.motif.core.network.service.comment

import com.ssafy.motif.core.network.model.BaseResponse
import com.ssafy.motif.core.network.model.PageResponse
import com.ssafy.motif.core.network.model.comment.CommentCreateRequest
import com.ssafy.motif.core.network.model.comment.CommentCreateResponse
import com.ssafy.motif.core.network.model.comment.CommentDeleteResponse
import com.ssafy.motif.core.network.model.comment.CommentItemResponse
import com.ssafy.motif.core.network.model.comment.ReplyItemResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface CommentService {

    @GET("api/v1/notices/{noticeSeq}/comments")
    suspend fun getNoticeComments(
        @Path("noticeSeq") noticeSeq: Long,
        @Query("page") page: Int,
        @Query("size") size: Int,
    ): BaseResponse<PageResponse<CommentItemResponse>>

    @POST("api/v1/notices/{noticeSeq}/comments")
    suspend fun postNoticeComment(
        @Path("noticeSeq") noticeSeq: Long,
        @Body request: CommentCreateRequest,
    ): BaseResponse<CommentCreateResponse>

    @GET("api/v1/challenges/{challengeSeq}/comments")
    suspend fun getChallengeComments(
        @Path("challengeSeq") challengeSeq: Long,
        @Query("page") page: Int,
        @Query("size") size: Int,
    ): BaseResponse<PageResponse<CommentItemResponse>>

    @POST("api/v1/challenges/{challengeSeq}/comments")
    suspend fun postChallengeComment(
        @Path("challengeSeq") challengeSeq: Long,
        @Body request: CommentCreateRequest,
    ): BaseResponse<CommentCreateResponse>

    @GET("api/v1/comments/{type}/{commentSeq}/replies")
    suspend fun getReplies(
        @Path("type") type: String,
        @Path("commentSeq") commentSeq: Long,
        @Query("page") page: Int,
        @Query("size") size: Int,
    ): BaseResponse<PageResponse<ReplyItemResponse>>

    @DELETE("api/v1/comments/{type}/{commentSeq}")
    suspend fun deleteComment(
        @Path("type") type: String,
        @Path("commentSeq") commentSeq: Long,
    ): BaseResponse<CommentDeleteResponse>

    @POST("api/v1/comments/{type}/{commentSeq}/like")
    suspend fun postCommentLike(
        @Path("type") type: String,
        @Path("commentSeq") commentSeq: Long,
    )

    @DELETE("api/v1/comments/{type}/{commentSeq}/like")
    suspend fun deleteCommentLike(
        @Path("type") type: String,
        @Path("commentSeq") commentSeq: Long,
    )
}