package com.ssafy.motif.core.data.repository

import com.ssafy.motif.core.model.auth.AuthLoginResult
import com.ssafy.motif.core.model.auth.AuthState
import kotlinx.coroutines.flow.Flow

interface AuthRepository {

    val authState: Flow<AuthState>

    fun signInWithGoogle(
        idToken: String,
    ): Flow<AuthLoginResult>

    suspend fun completeOnboarding()
}