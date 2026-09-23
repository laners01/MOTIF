package com.ssafy.motif.feature.mypage.impl.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.ssafy.motif.core.navigation.Navigator
import com.ssafy.motif.feature.mypage.api.navigation.MyPageNavKey
import com.ssafy.motif.feature.mypage.impl.mypage.MyPageScreen

fun EntryProviderScope<NavKey>.myPageEntry(
    navigator: Navigator,
) {
    entry<MyPageNavKey> {
        MyPageScreen(
            onNotificationClick = {
                // TODO 알림 NavKey 구현 후 연결
            },
            onSettingsClick = {
                // TODO 설정 NavKey 구현 후 연결
            },
            onCreateClick = {
                // TODO Challenge 메인 탭으로 이동
            },
            onChallengeClick = { videoId ->
                // TODO 내 참여영상 뷰어로 이동
            },
            onArtistSeeMoreClick = { artistId ->
                // TODO ArtistDetail로 이동
            },
        )
    }
}