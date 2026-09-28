package com.ssafy.motif.core.notifications.preference

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.ssafy.motif.core.notifications.fcm.PushNotificationType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

private val Context.notificationDataStore by
preferencesDataStore(
    name = "notification_preferences",
)

@Singleton
class NotificationPreferences @Inject constructor(
    @param:ApplicationContext
    private val context: Context,
) {

    suspend fun shouldShow(
        type: PushNotificationType,
    ): Boolean {

        val preferences =
            context
                .notificationDataStore
                .data
                .first()

        val allEnabled =
            preferences[KEY_ALL] ?: true

        if (!allEnabled) {
            return false
        }

        val categoryEnabled =
            when (type) {
                PushNotificationType.NEW_CHALLENGE,
                PushNotificationType.NEW_NOTICE,
                    ->
                    preferences[KEY_ARTIST] ?: true

                PushNotificationType.NEW_COMMENT,
                PushNotificationType.NEW_REPLY,
                    ->
                    preferences[KEY_POST] ?: true

                PushNotificationType.COMPOSITE_DONE,
                PushNotificationType.COMPOSITE_FAILED,
                    ->
                    preferences[KEY_SERVICE] ?: true
            }

        return categoryEnabled
    }

    suspend fun setAllEnabled(
        enabled: Boolean,
    ) {
        context.notificationDataStore.edit {
            it[KEY_ALL] = enabled
        }
    }

    suspend fun setArtistEnabled(
        enabled: Boolean,
    ) {
        context.notificationDataStore.edit {
            it[KEY_ARTIST] = enabled
        }
    }

    suspend fun setPostEnabled(
        enabled: Boolean,
    ) {
        context.notificationDataStore.edit {
            it[KEY_POST] = enabled
        }
    }

    suspend fun setServiceEnabled(
        enabled: Boolean,
    ) {
        context.notificationDataStore.edit {
            it[KEY_SERVICE] = enabled
        }
    }

    private companion object {

        val KEY_ALL =
            booleanPreferencesKey(
                "notification_all_enabled",
            )

        val KEY_ARTIST =
            booleanPreferencesKey(
                "notification_artist_enabled",
            )

        val KEY_POST =
            booleanPreferencesKey(
                "notification_post_enabled",
            )

        val KEY_SERVICE =
            booleanPreferencesKey(
                "notification_service_enabled",
            )
    }
}