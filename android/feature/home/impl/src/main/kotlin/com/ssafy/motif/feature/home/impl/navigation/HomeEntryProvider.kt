package com.ssafy.motif.feature.home.impl.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.ssafy.motif.feature.home.api.navigation.HomeNavKey
import com.ssafy.motif.feature.home.impl.HomeScreen

fun EntryProviderScope<NavKey>.homeEntry(
    onOnstageClick: (Long) -> Unit,
    onFanRadarClick: () -> Unit,
    onArtistClick: (Long) -> Unit,
    onMotifClick: (Long) -> Unit,
) {
    entry<HomeNavKey> {
        HomeScreen(
            onOnstageClick =
                onOnstageClick,
            onFanRadarClick =
                onFanRadarClick,
            onArtistClick =
                onArtistClick,
            onMotifClick =
                onMotifClick,
        )
    }
}