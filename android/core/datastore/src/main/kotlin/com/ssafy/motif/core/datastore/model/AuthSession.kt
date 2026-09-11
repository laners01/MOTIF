package com.ssafy.motif.core.datastore.model

data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val userId: Long?,
    val deviceId: String,
) {
    val hasTokens: Boolean
        get() = accessToken.isNotBlank() && refreshToken.isNotBlank()
}