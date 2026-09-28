package com.ssafy.motif.core.notifications.fcm

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ssafy.motif.core.notifications.R
import com.ssafy.motif.core.notifications.navigation.NotificationIntentExtras
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MotifNotificationManager @Inject constructor(
    @param:ApplicationContext
    private val context: Context,
) {

    fun createNotificationChannel() {

        val channel =
            NotificationChannel(
                CHANNEL_ID,
                context.getString(
                    R.string.notification_channel_name,
                ),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description =
                    context.getString(
                        R.string.notification_channel_description,
                    )
            }

        context
            .getSystemService(
                NotificationManager::class.java,
            )
            .createNotificationChannel(
                channel,
            )
    }

    fun show(
        notification: PushNotification,
    ) {
        createNotificationChannel()

        if (!hasNotificationPermission()) {
            return
        }

        val pendingIntent =
            createContentIntent(
                notification =
                    notification,
            )

        val title =
            notification.title
                .ifBlank {
                    context.getString(
                        R.string.notification_default_title,
                    )
                }

        val body =
            notification.body
                .ifBlank {
                    context.getString(
                        R.string.notification_default_body,
                    )
                }

        val systemNotification =
            NotificationCompat
                .Builder(
                    context,
                    CHANNEL_ID,
                )
                .setSmallIcon(
                    R.drawable.ic_notification,
                )
                .setContentTitle(
                    title,
                )
                .setContentText(
                    body,
                )
                .setStyle(
                    NotificationCompat
                        .BigTextStyle()
                        .bigText(
                            body,
                        ),
                )
                .setPriority(
                    NotificationCompat.PRIORITY_HIGH,
                )
                .setCategory(
                    NotificationCompat.CATEGORY_STATUS,
                )
                .setAutoCancel(
                    true,
                )
                .setOnlyAlertOnce(
                    true,
                )
                .setContentIntent(
                    pendingIntent,
                )
                .build()

        NotificationManagerCompat
            .from(
                context,
            )
            .notify(
                notification.notificationId(),
                systemNotification,
            )
    }

    private fun createContentIntent(
        notification: PushNotification,
    ): PendingIntent? {

        val launchIntent =
            context
                .packageManager
                .getLaunchIntentForPackage(
                    context.packageName,
                )
                ?: return null

        launchIntent.apply {
            flags =
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP

            putExtra(
                NotificationIntentExtras.TYPE,
                notification.type.name,
            )

            notification.jobUuid?.let {
                putExtra(
                    NotificationIntentExtras.JOB_UUID,
                    it,
                )
            }

            notification.jobType?.let {
                putExtra(
                    NotificationIntentExtras.JOB_TYPE,
                    it.name,
                )
            }

            notification.failCode?.let {
                putExtra(
                    NotificationIntentExtras.FAIL_CODE,
                    it,
                )
            }
        }

        return PendingIntent.getActivity(
            context,
            notification.notificationId(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun hasNotificationPermission():
            Boolean =
        Build.VERSION.SDK_INT <
                Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED

    private fun PushNotification.notificationId():
            Int =
        when {
            !jobUuid.isNullOrBlank() -> {
                "${type.name}:$jobUuid"
                    .hashCode()
            }

            videoId != null -> {
                "${type.name}:$videoId"
                    .hashCode()
            }

            challengeId != null -> {
                "${type.name}:$challengeId"
                    .hashCode()
            }

            noticeId != null -> {
                "${type.name}:$noticeId"
                    .hashCode()
            }

            else -> {
                hashCode()
            }
        }

    private companion object {

        const val CHANNEL_ID =
            "motif_push"
    }
}