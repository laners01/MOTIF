package com.ssafy.motif.core.network.model.notification

import kotlinx.serialization.Serializable

@Serializable
data class RegisterDeviceTokenRequest(
    val fcmToken: String,
    val platform: String = PLATFORM_ANDROID,
) {
    private companion object {
        const val PLATFORM_ANDROID = "ANDROID"
    }
}

@Serializable
data class DeactivateDeviceTokenRequest(
    val fcmToken: String,
)