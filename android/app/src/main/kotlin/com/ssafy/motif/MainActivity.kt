package com.ssafy.motif

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.motif.core.data.repository.AuthRepository
import com.ssafy.motif.core.data.util.NetworkMonitor
import com.ssafy.motif.core.designsystem.component.LoadingWheel
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.core.model.auth.AuthState
import com.ssafy.motif.core.notifications.fcm.PushTokenManager
import com.ssafy.motif.core.notifications.navigation.NotificationNavigationTarget
import com.ssafy.motif.core.notifications.navigation.clearNotificationNavigationExtras
import com.ssafy.motif.core.notifications.navigation.toNotificationNavigationTarget
import com.ssafy.motif.feature.home.api.navigation.HomeNavKey
import com.ssafy.motif.feature.onboarding.api.navigation.OnboardingNavKey
import com.ssafy.motif.feature.signin.api.navigation.SignInNavKey
import com.ssafy.motif.ui.MotifApp
import com.ssafy.motif.ui.rememberMotifAppState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var networkMonitor:
            NetworkMonitor

    @Inject
    lateinit var authRepository:
            AuthRepository

    @Inject
    lateinit var pushTokenManager:
            PushTokenManager

    private val notificationNavigationTarget =
        MutableStateFlow<NotificationNavigationTarget?>(
            null,
        )

    private val viewModel:
            MainActivityViewModel by viewModels()

    override fun onCreate(
        savedInstanceState: Bundle?,
    ) {
        super.onCreate(
            savedInstanceState,
        )

        handleNotificationIntent(
            intent = intent,
        )

        enableEdgeToEdge(
            statusBarStyle =
                SystemBarStyle.dark(
                    scrim = Color.TRANSPARENT,
                ),
            navigationBarStyle =
                SystemBarStyle.dark(
                    scrim = Color.TRANSPARENT,
                ),
        )

        setContent {
            val authState by
            authRepository
                .authState
                .collectAsStateWithLifecycle(
                    initialValue = AuthState.Initializing,
                )

            val myProfileImageUrl by
            viewModel
                .myProfileImageUrl
                .collectAsStateWithLifecycle()

            val pendingNotificationTarget by
            notificationNavigationTarget
                .collectAsStateWithLifecycle()

            val authenticated =
                authState == AuthState.SignedIn ||
                        authState == AuthState.OnboardingRequired

            NotificationPermissionEffect(
                enabled = authenticated,
            )

            LaunchedEffect(
                authState,
            ) {
                when (authState) {
                    AuthState.SignedIn -> {
                        viewModel.loadUserProfile()

                        registerPushToken()
                    }

                    AuthState.OnboardingRequired -> {
                        registerPushToken()
                    }

                    AuthState.SignedOut -> {
                        viewModel.clearUserProfile()
                    }

                    AuthState.Initializing ->
                        Unit
                }
            }

            MotifTheme {
                when (
                    val state =
                        authState
                ) {
                    AuthState.Initializing -> {
                        Box(
                            modifier =
                                Modifier.fillMaxSize(),
                            contentAlignment =
                                Alignment.Center,
                        ) {
                            LoadingWheel()
                        }
                    }

                    else -> {
                        key(
                            state,
                        ) {
                            /*
                             * Demo Mode에서는 실제 인증 상태와 관계없이
                             * 로그인 화면부터 시작한다.
                             *
                             * 실제 Session / AccessToken / 사용자 정보는
                             * 변경하지 않는다.
                             */
                            val startKey =
                                if (DEMO_MODE) {
                                    SignInNavKey
                                } else {
                                    when (state) {
                                        AuthState.SignedOut ->
                                            SignInNavKey

                                        AuthState.OnboardingRequired ->
                                            OnboardingNavKey

                                        AuthState.SignedIn ->
                                            HomeNavKey

                                        AuthState.Initializing ->
                                            error(
                                                "Initializing state must be handled before navigation.",
                                            )
                                    }
                                }

                            val appState =
                                rememberMotifAppState(
                                    networkMonitor =
                                        networkMonitor,
                                    authRepository =
                                        authRepository,
                                    startKey =
                                        startKey,
                                )

                            MotifApp(
                                appState =
                                    appState,
                                myProfileImageUrl =
                                    myProfileImageUrl.orEmpty(),
                                isDemoMode =
                                    DEMO_MODE,
                                notificationNavigationTarget =
                                    if (
                                        state ==
                                        AuthState.SignedIn
                                    ) {
                                        pendingNotificationTarget
                                    } else {
                                        null
                                    },
                                onNotificationNavigationHandled = {
                                    notificationNavigationTarget.value =
                                        null
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(
        intent: Intent,
    ) {
        super.onNewIntent(
            intent,
        )

        setIntent(
            intent,
        )

        handleNotificationIntent(
            intent = intent,
        )
    }

    private fun handleNotificationIntent(
        intent: Intent,
    ) {
        val target =
            intent
                .toNotificationNavigationTarget()
                ?: return

        notificationNavigationTarget.value =
            target

        intent.clearNotificationNavigationExtras()
    }

    private suspend fun registerPushToken() {
        try {
            pushTokenManager
                .registerCurrentToken()
        } catch (
            exception: CancellationException,
        ) {
            throw exception
        } catch (
            exception: Exception,
        ) {
            Log.w(
                TAG,
                "Failed to register FCM token.",
                exception,
            )
        }
    }

    private companion object {
        const val TAG =
            "MainActivity"

        /*
         * 시연 종료 후 false로 변경하면
         * 기존 로그인/온보딩 흐름으로 복구된다.
         */
        const val DEMO_MODE =
            false
    }
}

@Composable
private fun NotificationPermissionEffect(
    enabled: Boolean,
) {
    if (
        Build.VERSION.SDK_INT <
        Build.VERSION_CODES.TIRAMISU
    ) {
        return
    }

    val context =
        androidx.compose.ui.platform
            .LocalContext.current

    var requested by
    rememberSaveable {
        mutableStateOf(
            false,
        )
    }

    val launcher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts
                    .RequestPermission(),
        ) {
            // 사용자의 선택을 그대로 따른다.
        }

    LaunchedEffect(
        enabled,
    ) {
        if (!enabled) {
            return@LaunchedEffect
        }

        val granted =
            ContextCompat
                .checkSelfPermission(
                    context,
                    Manifest.permission
                        .POST_NOTIFICATIONS,
                ) ==
                    PackageManager.PERMISSION_GRANTED

        if (
            !granted &&
            !requested
        ) {
            requested =
                true

            launcher.launch(
                Manifest.permission
                    .POST_NOTIFICATIONS,
            )
        }
    }
}