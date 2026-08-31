package com.ssafy.modif.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

data class TopLevelNavItem(
    @param:DrawableRes val iconRes: Int,
    @param:StringRes val iconTextId: Int,
    @param:StringRes val titleTextId: Int,
)