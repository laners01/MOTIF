package com.ssafy.motif

import android.app.Application
import com.ssafy.motif.core.notifications.fcm.MotifNotificationManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MotifApplication : Application() {

    @Inject
    lateinit var notificationManager:
            MotifNotificationManager

    override fun onCreate() {
        super.onCreate()

        notificationManager
            .createNotificationChannel()
    }
}