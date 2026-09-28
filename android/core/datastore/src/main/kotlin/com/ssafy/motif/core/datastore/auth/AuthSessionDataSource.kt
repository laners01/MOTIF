package com.ssafy.motif.core.datastore.auth

import androidx.datastore.core.DataStore
import com.ssafy.motif.core.datastore.AuthSessionPreferences
import com.ssafy.motif.core.datastore.copy
import com.ssafy.motif.core.datastore.model.AuthSession
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class AuthSessionDataSource @Inject constructor(
    private val authSessionPreferences:
    DataStore<AuthSessionPreferences>,
) {

    val session: Flow<AuthSession> =
        authSessionPreferences.data
            .map { preferences ->
                AuthSession(
                    accessToken = preferences.accessToken,
                    refreshToken = preferences.refreshToken,
                    userId =
                        if (preferences.hasUserId()) {
                            preferences.userId
                        } else {
                            null
                        },
                    deviceId = preferences.deviceId,
                    onboardingCompleted =
                        if (preferences.hasOnboardingCompleted()) {
                            preferences.onboardingCompleted
                        } else {
                            null
                        },
                    onboardingUserId =
                        if (preferences.hasOnboardingUserId()) {
                            preferences.onboardingUserId
                        } else {
                            null
                        },
                )
            }

    suspend fun getAccessToken(): String =
        authSessionPreferences.data
            .first()
            .accessToken

    suspend fun getRefreshToken(): String =
        authSessionPreferences.data
            .first()
            .refreshToken

    suspend fun saveSession(
        accessToken: String,
        refreshToken: String,
        userId: Long,
        onboardingCompleted: Boolean,
    ) {
        authSessionPreferences.updateData {
            it.copy {
                this.accessToken = accessToken
                this.refreshToken = refreshToken
                this.userId = userId

                this.onboardingCompleted =
                    onboardingCompleted

                onboardingUserId =
                    userId
            }
        }
    }

    suspend fun completeOnboarding() {
        authSessionPreferences.updateData { preferences ->
            if (!preferences.hasUserId()) {
                return@updateData preferences
            }

            val userId =
                preferences.userId

            preferences.copy {
                onboardingCompleted = true
                onboardingUserId = userId
            }
        }
    }

    suspend fun clearSession() {
        authSessionPreferences.updateData {
            it.copy {
                accessToken = ""
                refreshToken = ""
                clearUserId()

                /*
                 * onboardingCompleted / onboardingUserId는 유지한다.
                 *
                 * 온보딩 중 인증이 끊겨 다시 로그인하더라도
                 * 동일 사용자인 경우 온보딩을 계속 진행하기 위함.
                 */
            }
        }
    }

    suspend fun getOrCreateDeviceId(): String {
        val preferences =
            authSessionPreferences.updateData {
                if (it.deviceId.isNotBlank()) {
                    it
                } else {
                    it.copy {
                        deviceId =
                            UUID
                                .randomUUID()
                                .toString()
                    }
                }
            }

        return preferences.deviceId
    }
}