package com.ssafy.motif.feature.motif.impl

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.ssafy.motif.core.designsystem.icon.MotifIcons

enum class MotifSubMenu(
    @param:DrawableRes val iconRes: Int,
    @param:StringRes val labelRes: Int,
) {
    ASSETS(MotifIcons.Motif, R.string.feature_motif_impl_assets),
    SHOP(MotifIcons.Bag, R.string.feature_motif_impl_shop),
    ARCHIVE(MotifIcons.MediaStrip, R.string.feature_motif_impl_archive),
}

@Immutable
internal data class MotifUiState(
    val selectedMenu: MotifSubMenu = MotifSubMenu.ASSETS,
)
