package com.ssafy.motif.core.network.model.onboarding

import kotlinx.serialization.Serializable

@Serializable
data class OnboardingRequest(
    val artistSeqs: List<Long>,
    val nickname: String,
)