package com.ssafy.motif.core.data.repository

import kotlinx.coroutines.flow.Flow

interface NotificationRepository {

    fun registerDeviceToken(
        fcmToken: String,
    ): Flow<Unit>

    fun deactivateDeviceToken(
        fcmToken: String,
    ): Flow<Unit>
}