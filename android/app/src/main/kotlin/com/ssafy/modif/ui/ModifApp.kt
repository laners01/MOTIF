package com.ssafy.modif.ui

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
import com.ssafy.modif.core.navigation.Navigator
import com.ssafy.modif.core.navigation.toEntries
import com.ssafy.modif.feature.home.impl.navigation.homeEntry

@Composable
fun ModifApp(
    appState: ModifAppState,
    modifier: Modifier = Modifier,
) {
    val navigator = remember(appState.navigationState) {
        Navigator(appState.navigationState)
    }

    val entryProvider = entryProvider {
        homeEntry(navigator)
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