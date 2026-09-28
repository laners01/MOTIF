package com.ssafy.motif.core.network.model.onboarding

import kotlinx.serialization.Serializable

@Serializable
data class OnboardingResponse(
    val subscribedCount: Int,
    val nickname: String,
)