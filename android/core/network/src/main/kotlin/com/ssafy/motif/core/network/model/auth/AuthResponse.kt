package com.ssafy.motif.core.network.model.auth

import kotlinx.serialization.Serializable

@Serializable
data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
    val expiresIn: Long,
    val isNewUser: Boolean,
    val user: AuthUserResponse,
)

@Serializable
data class AuthUserResponse(
    val userSeq: Long,
    val nickname: String,
    val profileImageUrl: String?,
)