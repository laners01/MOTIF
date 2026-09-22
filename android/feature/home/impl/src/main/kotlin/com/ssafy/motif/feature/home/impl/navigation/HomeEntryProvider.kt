package com.ssafy.motif.feature.home.impl.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.ssafy.motif.core.navigation.Navigator
import com.ssafy.motif.feature.home.api.navigation.HomeNavKey
import com.ssafy.motif.feature.home.impl.HomeScreen

fun EntryProviderScope<NavKey>.homeEntry(
    navigator: Navigator,
) {
    entry<HomeNavKey> {
        HomeScreen(
            onOnstageClick = { videoId ->
                // TODO 참여 영상 숏폼 화면으로 이동
            },
            onFanRadarClick = {
                // TODO 아티스트 랭킹 화면으로 이동
            },
            onArtistClick = { artistId ->
                // TODO 아티스트 상세 화면으로 이동
            },
            onMotifClick = { motifId ->
                // TODO 모티프 상세 화면으로 이동
            },
        )
    }
}