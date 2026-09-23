package com.ssafy.motif

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.motif.core.data.repository.AuthRepository
import com.ssafy.motif.core.data.util.NetworkMonitor
import com.ssafy.motif.core.designsystem.component.LoadingWheel
import com.ssafy.motif.core.designsystem.theme.MotifTheme
import com.ssafy.motif.core.model.auth.AuthState
import com.ssafy.motif.feature.home.api.navigation.HomeNavKey
import com.ssafy.motif.feature.onboarding.api.navigation.OnboardingNavKey
import com.ssafy.motif.feature.signin.api.navigation.SignInNavKey
import com.ssafy.motif.ui.MotifApp
import com.ssafy.motif.ui.rememberMotifAppState
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var networkMonitor: NetworkMonitor

    @Inject
    lateinit var authRepository: AuthRepository

    override fun onCreate(
        savedInstanceState: Bundle?,
    ) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle =
                SystemBarStyle.dark(
                    scrim =
                        Color.TRANSPARENT,
                ),
            navigationBarStyle =
                SystemBarStyle.dark(
                    scrim =
                        Color.TRANSPARENT,
                ),
        )

        setContent {
            val authState by
            authRepository
                .authState
                .collectAsStateWithLifecycle(
                    initialValue =
                        AuthState.Initializing,
                )

            MotifTheme {
                when (val state = authState) {
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
                        /*
                         * AuthState가 바뀌면 기존 Navigation
                         * 자체를 폐기하고 새로운 root로 생성한다.
                         *
                         * SignIn 화면이 BackStack에 남지 않는다.
                         */
                        key(state) {
                            val startKey =
                                when (state) {
                                    AuthState.SignedOut -> {
                                        SignInNavKey
                                    }

                                    AuthState.OnboardingRequired -> {
                                        OnboardingNavKey
                                    }

                                    AuthState.SignedIn -> {
                                        HomeNavKey
                                    }

                                    AuthState.Initializing -> {
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
                            )
                        }
                    }
                }
            }
        }
    }
}