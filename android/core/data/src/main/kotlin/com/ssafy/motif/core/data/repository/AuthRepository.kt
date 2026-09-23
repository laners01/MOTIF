package com.ssafy.motif.core.data.repository

import com.ssafy.motif.core.model.auth.AuthLoginResult
import com.ssafy.motif.core.model.auth.AuthState
import com.ssafy.motif.core.model.onboarding.OnboardingCompletionResult
import kotlinx.coroutines.flow.Flow

interface AuthRepository {

    val authState: Flow<AuthState>

    fun signInWithGoogle(
        idToken: String,
    ): Flow<AuthLoginResult>

    fun submitOnboarding(
        artistIds: List<Long>,
        nickname: String,
    ): Flow<OnboardingCompletionResult>

    suspend fun completeOnboarding()
}