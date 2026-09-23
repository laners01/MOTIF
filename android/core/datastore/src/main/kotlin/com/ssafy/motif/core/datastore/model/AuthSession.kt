package com.ssafy.motif.core.datastore.model

data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val userId: Long?,
    val deviceId: String,
    val onboardingCompleted: Boolean?,
    val onboardingUserId: Long?,
) {
    val hasTokens: Boolean
        get() =
            accessToken.isNotBlank() &&
                    refreshToken.isNotBlank()
}