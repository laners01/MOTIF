package com.ssafy.motif.feature.ranking.impl.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.ssafy.motif.core.navigation.Navigator
import com.ssafy.motif.feature.ranking.api.navigation.RankingNavKey
import com.ssafy.motif.feature.ranking.impl.ranking.RankingScreen

fun EntryProviderScope<NavKey>.rankingEntry(navigator: Navigator) {
    entry<RankingNavKey> {
        RankingScreen()
    }
}