package com.ssafy.motif.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.ssafy.motif.core.designsystem.icon.MotifIcons
import com.ssafy.motif.feature.home.api.navigation.HomeNavKey
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