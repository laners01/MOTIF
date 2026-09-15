package com.ssafy.motif.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.ssafy.motif.core.navigation.Navigator
import com.ssafy.motif.core.navigation.toEntries
import com.ssafy.motif.feature.artistdetail.impl.navigation.artistDetailEntry
import com.ssafy.motif.feature.home.impl.navigation.homeEntry
import com.ssafy.motif.feature.onboarding.impl.navigation.onboardingEntry
import com.ssafy.motif.feature.ranking.impl.navigation.rankingEntry


@Composable
fun MotifApp(
    appState: MotifAppState,
    modifier: Modifier = Modifier,
) {
    val navigator = remember(appState.navigationState) {
        Navigator(appState.navigationState)
    }

    val entryProvider = entryProvider {
        homeEntry(navigator)
        onboardingEntry(navigator)
        rankingEntry(navigator)
        artistDetailEntry(navigator)
    }

    Box(modifier.fillMaxSize()) {
        NavDisplay(
            entries = appState.navigationState.toEntries(entryProvider),
            onBack = navigator::goBack,
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Top,
                    ),
                ),
        )
    }
}