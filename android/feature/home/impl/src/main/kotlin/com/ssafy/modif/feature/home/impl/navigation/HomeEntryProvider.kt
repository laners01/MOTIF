package com.ssafy.modif.feature.home.impl.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.ssafy.modif.core.navigation.Navigator
import com.ssafy.modif.feature.home.api.navigation.HomeNavKey
import com.ssafy.modif.feature.home.impl.HomeScreen

fun EntryProviderScope<NavKey>.homeEntryProvider(navigator: Navigator) {
    entry<HomeNavKey> {
        HomeScreen()
    }
}