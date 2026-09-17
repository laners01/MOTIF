package com.ssafy.motif.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.ssafy.motif.core.data.util.NetworkMonitor
import com.ssafy.motif.core.navigation.NavigationState
import com.ssafy.motif.core.navigation.rememberNavigationState
import com.ssafy.motif.feature.artistdetail.api.navigation.ArtistDetailNavKey
import com.ssafy.motif.feature.home.api.navigation.HomeNavKey
import com.ssafy.motif.feature.mypage.api.navigation.MyPageNavKey
import com.ssafy.motif.navigation.TOP_LEVEL_NAV_ITEMS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@Composable
fun rememberMotifAppState(
    networkMonitor: NetworkMonitor,
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
): MotifAppState {
    // TODO 마이페이지 화면 테스트용. 커밋 전 HomeNavKey로 되돌릴 것
    val startKey = MyPageNavKey
    val navigationState = rememberNavigationState(
        startKey = startKey,
        topLevelKeys = TOP_LEVEL_NAV_ITEMS.keys + startKey,
    )

    return remember(
        navigationState,
        coroutineScope,
        networkMonitor,
    ) {
        MotifAppState(
            navigationState = navigationState,
            coroutineScope = coroutineScope,
            networkMonitor = networkMonitor,
        )
    }
}

@Stable
class MotifAppState(
    val navigationState: NavigationState,
    coroutineScope: CoroutineScope,
    networkMonitor: NetworkMonitor,
) {
    val isOffline = networkMonitor.isOnline
        .map(Boolean::not)
        .stateIn(
            scope = coroutineScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )
}