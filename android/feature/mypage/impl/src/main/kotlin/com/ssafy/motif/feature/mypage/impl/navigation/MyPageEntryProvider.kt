package com.ssafy.motif.feature.mypage.impl.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.ssafy.motif.core.navigation.Navigator
import com.ssafy.motif.feature.mypage.api.navigation.MyPageNavKey
import com.ssafy.motif.feature.mypage.impl.mypage.MyPageScreen

fun EntryProviderScope<NavKey>.myPageEntry(navigator: Navigator) {
    entry<MyPageNavKey> {
        MyPageScreen()
    }
}
