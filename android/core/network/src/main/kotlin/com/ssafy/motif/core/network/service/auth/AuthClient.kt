package com.ssafy.motif.core.network.service.auth

import com.ssafy.motif.core.network.model.auth.AuthResponse
import com.ssafy.motif.core.network.model.auth.GoogleLoginRequest
import javax.inject.Inject

class AuthClient @Inject constructor(
    private val authService: AuthService,
) {

    suspend fun signInWithGoogle(
        idToken: String,
    ): AuthResponse =
        authService
            .signInWithGoogle(
                request = GoogleLoginRequest(
                    idToken = idToken,
                ),
            )
            .data
}