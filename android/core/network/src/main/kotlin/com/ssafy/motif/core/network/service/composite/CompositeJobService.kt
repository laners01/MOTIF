package com.ssafy.motif.core.network.service.composite

import com.ssafy.motif.core.network.model.BaseResponse
import com.ssafy.motif.core.network.model.composite.CompositeJobResponse
import com.ssafy.motif.core.network.model.composite.CreateCompositeJobRequest
import com.ssafy.motif.core.network.model.composite.CreateCompositeJobResponse
import com.ssafy.motif.core.network.model.composite.RequestCompositeJobRequest
import com.ssafy.motif.core.network.model.composite.RequestCompositeJobResponse
import com.ssafy.motif.core.network.model.composite.RetryCompositeJobResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface CompositeJobService {

    @POST("/api/v1/composite-jobs")
    suspend fun createCompositeJob(
        @Body request: CreateCompositeJobRequest,
    ): BaseResponse<CreateCompositeJobResponse>

    @POST("/api/v1/composite-jobs/{jobUuid}/request")
    suspend fun requestCompositeJob(
        @Path("jobUuid") jobUuid: String,
        @Body request: RequestCompositeJobRequest,
    ): BaseResponse<RequestCompositeJobResponse>

    @GET("/api/v1/composite-jobs/{jobUuid}")
    suspend fun getCompositeJob(
        @Path("jobUuid") jobUuid: String,
    ): BaseResponse<CompositeJobResponse>

    @POST("/api/v1/composite-jobs/{jobUuid}/retry")
    suspend fun retryCompositeJob(
        @Path("jobUuid") jobUuid: String,
    ): BaseResponse<RetryCompositeJobResponse>
}