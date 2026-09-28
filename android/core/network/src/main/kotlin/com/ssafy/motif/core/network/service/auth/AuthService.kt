package com.ssafy.motif.core.network.service.auth

import com.ssafy.motif.core.network.model.BaseResponse
import com.ssafy.motif.core.network.model.auth.AuthResponse
import com.ssafy.motif.core.network.model.auth.GoogleLoginRequest
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthService {

    @POST("api/v1/auth/login/google")
    suspend fun signInWithGoogle(
        @Body request: GoogleLoginRequest,
    ): BaseResponse<AuthResponse>
}