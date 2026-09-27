package com.ssafy.motif.core.notifications.navigation

import android.content.Intent
import com.ssafy.motif.core.notifications.fcm.CompositeJobType
import com.ssafy.motif.core.notifications.fcm.PushNotificationType

sealed interface NotificationNavigationTarget {

    data class ChallengeCompositeResult(
        val jobUuid: String,
    ) : NotificationNavigationTarget
}

fun Intent.toNotificationNavigationTarget():
        NotificationNavigationTarget? {

    val type =
        PushNotificationType.fromServerValue(
            getStringExtra(
                NotificationIntentExtras.TYPE,
            ),
        )
            ?: return null

    return when (type) {

        PushNotificationType.COMPOSITE_DONE,
        PushNotificationType.COMPOSITE_FAILED,
            -> {
            val jobType =
                CompositeJobType.fromServerValue(
                    getStringExtra(
                        NotificationIntentExtras.JOB_TYPE,
                    ),
                )

            if (
                jobType !=
                CompositeJobType.CHALLENGE
            ) {
                return null
            }

            val jobUuid =
                getStringExtra(
                    NotificationIntentExtras.JOB_UUID,
                )
                    ?.takeIf(String::isNotBlank)
                    ?: return null

            NotificationNavigationTarget
                .ChallengeCompositeResult(
                    jobUuid =
                        jobUuid,
                )
        }

        else -> null
    }
}

fun Intent.clearNotificationNavigationExtras() {
    removeExtra(
        NotificationIntentExtras.TYPE,
    )
    removeExtra(
        NotificationIntentExtras.JOB_UUID,
    )
    removeExtra(
        NotificationIntentExtras.JOB_TYPE,
    )
    removeExtra(
        NotificationIntentExtras.FAIL_CODE,
    )
}

object NotificationIntentExtras {

    const val TYPE =
        "notification_type"

    const val JOB_UUID =
        "notification_job_uuid"

    const val JOB_TYPE =
        "notification_job_type"

    const val FAIL_CODE =
        "notification_fail_code"
}