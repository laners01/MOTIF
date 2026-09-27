package com.ssafy.motif.core.network.model.notification

import kotlinx.serialization.Serializable

@Serializable
data class RegisterDeviceTokenRequest(
    val fcmToken: String,
    val platform: DevicePlatform,
)

@Serializable
enum class DevicePlatform {
    ANDROID,
    IOS,
    WEB,
}

@Serializable
data class DeactivateDeviceTokenRequest(
    val fcmToken: String,
)