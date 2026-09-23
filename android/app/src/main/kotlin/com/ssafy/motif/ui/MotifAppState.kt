package com.ssafy.motif.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavKey
import com.ssafy.motif.core.data.repository.AuthRepository
import com.ssafy.motif.core.data.util.NetworkMonitor
import com.ssafy.motif.core.navigation.NavigationState
import com.ssafy.motif.core.navigation.rememberNavigationState
import com.ssafy.motif.feature.motif.api.navigation.MotifSubMenu
import com.ssafy.motif.feature.onboarding.api.navigation.OnboardingNavKey
import com.ssafy.motif.feature.signin.api.navigation.SignInNavKey
import com.ssafy.motif.navigation.TOP_LEVEL_NAV_KEYS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Composable
fun rememberMotifAppState(
    networkMonitor: NetworkMonitor,
    authRepository: AuthRepository,
    startKey: NavKey,
    coroutineScope: CoroutineScope =
        rememberCoroutineScope(),
): MotifAppState {

    /*
     * NavigationState 구조상 startKey도
     * subStacks 안에 존재해야 한다.
     */
    val navigationKeys =
        remember {
            TOP_LEVEL_NAV_KEYS +
                    SignInNavKey +
                    OnboardingNavKey
        }

    val navigationState =
        rememberNavigationState(
            startKey = startKey,
            topLevelKeys = navigationKeys,
        )

    return remember(
        navigationState,
        coroutineScope,
        networkMonitor,
        authRepository,
    ) {
        MotifAppState(
            navigationState = navigationState,
            coroutineScope = coroutineScope,
            networkMonitor = networkMonitor,
            authRepository = authRepository,
        )
    }
}

@Stable
class MotifAppState(
    val navigationState: NavigationState,
    private val coroutineScope: CoroutineScope,
    networkMonitor: NetworkMonitor,
    private val authRepository: AuthRepository,
) {

    var selectedMotifSubMenu by
    mutableStateOf(
        MotifSubMenu.ASSETS,
    )
        private set

    val isOffline =
        networkMonitor.isOnline
            .map(Boolean::not)
            .stateIn(
                scope = coroutineScope,
                started =
                    SharingStarted
                        .WhileSubscribed(
                            stopTimeoutMillis =
                                5_000,
                        ),
                initialValue = false,
            )

    fun selectMotifSubMenu(
        menu: MotifSubMenu,
    ) {
        selectedMotifSubMenu =
            menu
    }

    fun completeOnboarding() {
        coroutineScope.launch {
            authRepository
                .completeOnboarding()
        }
    }
}