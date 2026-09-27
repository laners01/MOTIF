package com.ssafy.motif.core.notifications.fcm

import com.google.firebase.messaging.FirebaseMessaging
import com.ssafy.motif.core.data.repository.NotificationRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@Singleton
class PushTokenManager @Inject constructor(
    private val notificationRepository: NotificationRepository,
) {

    suspend fun registerCurrentToken() {
        val token =
            FirebaseMessaging
                .getInstance()
                .awaitToken()

        registerToken(
            token = token,
        )
    }

    suspend fun registerToken(
        token: String,
    ) {
        if (token.isBlank()) {
            return
        }

        notificationRepository
            .registerDeviceToken(
                fcmToken = token,
            )
            .first()
    }

    suspend fun unregisterCurrentToken() {
        val token =
            FirebaseMessaging
                .getInstance()
                .awaitToken()

        if (token.isBlank()) {
            return
        }

        notificationRepository
            .deactivateDeviceToken(
                fcmToken = token,
            )
            .first()
    }
}

private suspend fun FirebaseMessaging.awaitToken():
        String =
    suspendCoroutine { continuation ->

        token
            .addOnSuccessListener { token ->
                continuation.resume(
                    token,
                )
            }
            .addOnFailureListener { throwable ->
                continuation.resumeWithException(
                    throwable,
                )
            }
    }