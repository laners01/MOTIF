package com.ssafy.motif.core.network.service.onboarding

import com.ssafy.motif.core.network.model.BaseResponse
import com.ssafy.motif.core.network.model.onboarding.OnboardingRequest
import com.ssafy.motif.core.network.model.onboarding.OnboardingResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface OnboardingService {

    @POST("/api/v1/onboarding")
    suspend fun completeOnboarding(
        @Body request: OnboardingRequest,
    ): BaseResponse<OnboardingResponse>
}