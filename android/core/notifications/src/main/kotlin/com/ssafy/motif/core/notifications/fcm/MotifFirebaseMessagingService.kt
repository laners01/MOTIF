package com.ssafy.motif.core.notifications.fcm

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.ssafy.motif.core.data.repository.AuthRepository
import com.ssafy.motif.core.model.auth.AuthState
import com.ssafy.motif.core.notifications.di.NotificationScope
import com.ssafy.motif.core.notifications.preference.NotificationPreferences
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MotifFirebaseMessagingService :
    FirebaseMessagingService() {

    @Inject
    lateinit var pushTokenManager:
            PushTokenManager

    @Inject
    lateinit var notificationManager:
            MotifNotificationManager

    @Inject
    lateinit var notificationPreferences:
            NotificationPreferences

    @Inject
    lateinit var authRepository:
            AuthRepository

    @Inject
    @NotificationScope
    lateinit var notificationScope:
            CoroutineScope

    override fun onMessageReceived(
        message: RemoteMessage,
    ) {
        super.onMessageReceived(
            message,
        )

        val notification =
            message
                .data
                .toPushNotification()
                ?: return

        notificationScope.launch {
            try {
                if (!isAuthenticated()) {
                    return@launch
                }

                if (
                    !notificationPreferences
                        .shouldShow(
                            type =
                                notification.type,
                        )
                ) {
                    return@launch
                }

                notificationManager.show(
                    notification =
                        notification,
                )
            } catch (
                exception: CancellationException,
            ) {
                throw exception
            } catch (
                exception: Exception,
            ) {
                Log.w(
                    TAG,
                    "Failed to handle FCM message.",
                    exception,
                )
            }
        }
    }

    override fun onNewToken(
        token: String,
    ) {
        super.onNewToken(
            token,
        )

        notificationScope.launch {
            try {
                if (!isAuthenticated()) {
                    return@launch
                }

                pushTokenManager.registerToken(
                    token =
                        token,
                )
            } catch (
                exception: CancellationException,
            ) {
                throw exception
            } catch (
                exception: Exception,
            ) {
                Log.w(
                    TAG,
                    "Failed to register refreshed FCM token.",
                    exception,
                )
            }
        }
    }

    private suspend fun isAuthenticated():
            Boolean =
        when (
            authRepository
                .authState
                .first()
        ) {
            AuthState.SignedIn,
            AuthState.OnboardingRequired,
                -> true

            AuthState.Initializing,
            AuthState.SignedOut,
                -> false
        }

    private companion object {

        const val TAG =
            "MotifFcm"
    }
}