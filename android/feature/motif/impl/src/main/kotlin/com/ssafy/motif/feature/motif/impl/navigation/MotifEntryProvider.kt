package com.ssafy.motif.feature.motif.impl.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.ssafy.motif.core.navigation.Navigator
import com.ssafy.motif.feature.motif.api.navigation.MotifNavKey
import com.ssafy.motif.feature.motif.impl.MotifScreen

fun EntryProviderScope<NavKey>.motifEntry(navigator: Navigator) {
    entry<MotifNavKey> {
        MotifScreen(
            onBackClick = navigator::goBack,
        )
    }
}
