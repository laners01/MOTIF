package com.ssafy.modif.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.ssafy.modif.core.designsystem.icon.ModifIcons
import com.ssafy.modif.feature.home.api.navigation.HomeNavKey
import com.ssafy.modif.feature.home.api.R as HomeR

data class TopLevelNavItem(
    @param:DrawableRes val iconRes: Int,
    @param:StringRes val iconTextId: Int,
    @param:StringRes val titleTextId: Int,
)

val HOME = TopLevelNavItem(
    iconRes = ModifIcons.Home,
    iconTextId = HomeR.string.feature_home_api_title,
    titleTextId = HomeR.string.feature_home_api_title,
)

val TOP_LEVEL_NAV_ITEMS = mapOf(
    HomeNavKey to HOME,
)