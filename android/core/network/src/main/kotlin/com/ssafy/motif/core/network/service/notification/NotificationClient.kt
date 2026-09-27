package com.ssafy.motif.core.network.service.notification

import com.ssafy.motif.core.network.model.notification.DeactivateDeviceTokenRequest
import com.ssafy.motif.core.network.model.notification.DevicePlatform
import com.ssafy.motif.core.network.model.notification.RegisterDeviceTokenRequest
import javax.inject.Inject

class NotificationClient @Inject constructor(
    private val notificationService: NotificationService,
) {

    suspend fun registerDeviceToken(
        fcmToken: String,
    ) {
        notificationService.registerDeviceToken(
            request =
                RegisterDeviceTokenRequest(
                    fcmToken = fcmToken,
                    platform = DevicePlatform.ANDROID,
                ),
        )
    }

    suspend fun deactivateDeviceToken(
        fcmToken: String,
    ) {
        notificationService.deactivateDeviceToken(
            request =
                DeactivateDeviceTokenRequest(
                    fcmToken = fcmToken,
                ),
        )
    }
}