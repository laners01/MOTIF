package com.ssafy.motif.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.navigation3.runtime.NavKey
import com.ssafy.motif.core.designsystem.icon.MotifIcons
import com.ssafy.motif.feature.home.api.navigation.HomeNavKey
import com.ssafy.motif.feature.motif.api.navigation.MotifNavKey
import com.ssafy.motif.feature.mypage.api.navigation.MyPageNavKey
import com.ssafy.motif.feature.home.api.R as HomeR

data class TopLevelNavItem(
    @param:DrawableRes val iconRes: Int,
    @param:StringRes val iconTextId: Int,
    @param:StringRes val titleTextId: Int,
)

val HOME = TopLevelNavItem(
    iconRes = MotifIcons.Home,
    iconTextId = HomeR.string.feature_home_api_title,
    titleTextId = HomeR.string.feature_home_api_title,
)

val TOP_LEVEL_NAV_ITEMS = mapOf(
    HomeNavKey to HOME,
)

val TOP_LEVEL_NAV_KEYS: Set<NavKey> = setOf(
    HomeNavKey,
    MyPageNavKey,
    MotifNavKey,

    // TODO Challenge Navigation 구현 후 추가
    // ChallengeNavKey,
)