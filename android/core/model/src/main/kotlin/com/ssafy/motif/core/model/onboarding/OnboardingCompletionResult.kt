package com.ssafy.motif.core.model.onboarding

sealed interface OnboardingCompletionResult {

    data class Completed(
        val subscribedCount: Int,
        val nickname: String,
    ) : OnboardingCompletionResult

    data object AlreadyCompleted :
        OnboardingCompletionResult

    data object ArtistNotFound :
        OnboardingCompletionResult

    data object InvalidParameter :
        OnboardingCompletionResult
}