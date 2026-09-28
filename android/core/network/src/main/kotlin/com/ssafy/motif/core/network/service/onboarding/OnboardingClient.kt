package com.ssafy.motif.core.network.service.onboarding

import com.ssafy.motif.core.network.model.onboarding.OnboardingRequest
import com.ssafy.motif.core.network.model.onboarding.OnboardingResponse
import javax.inject.Inject

class OnboardingClient @Inject constructor(
    private val onboardingService: OnboardingService,
) {

    suspend fun completeOnboarding(
        artistIds: List<Long>,
        nickname: String,
    ): OnboardingResponse =
        onboardingService
            .completeOnboarding(
                request = OnboardingRequest(
                    artistSeqs = artistIds,
                    nickname = nickname,
                ),
            )
            .data
}