package com.ssafy.motif.feature.motif.impl.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.ssafy.motif.core.navigation.Navigator
import com.ssafy.motif.feature.motif.api.navigation.MotifNavKey
import com.ssafy.motif.feature.motif.api.navigation.MotifSubMenu
import com.ssafy.motif.feature.motif.api.navigation.PassPurchaseNavKey
import com.ssafy.motif.feature.motif.api.navigation.ProductDetailNavKey
import com.ssafy.motif.feature.motif.impl.MotifScreen
import com.ssafy.motif.feature.motif.impl.shop.detail.ProductDetailScreen
import com.ssafy.motif.feature.motif.impl.shop.pass.PassPurchaseScreen

fun EntryProviderScope<NavKey>.motifEntry(
    navigator: Navigator,
    selectedMenu: () -> MotifSubMenu,
    onMotifCaptureClick: (assetId: Long) -> Unit,
) {
    val openProductDetail: (
        artistId: Long,
        assetId: Long,
    ) -> Unit = { artistId, assetId ->
        navigator.navigate(
            ProductDetailNavKey(
                artistId = artistId,
                assetId = assetId,
            ),
        )
    }

    val openPassPurchase: (
        artistId: Long,
    ) -> Unit = { artistId ->
        navigator.navigate(
            PassPurchaseNavKey(
                artistId = artistId,
            ),
        )
    }

    entry<MotifNavKey> {
        MotifScreen(
            selectedMenu = selectedMenu(),
            onProductClick = openProductDetail,
            onPassClick = openPassPurchase,
        )
    }

    entry<ProductDetailNavKey> { key ->
        ProductDetailScreen(
            artistId = key.artistId,
            assetId = key.assetId,
            onPassClick = openPassPurchase,
            onCaptureClick =
                onMotifCaptureClick,
        )
    }

    entry<PassPurchaseNavKey> { key ->
        PassPurchaseScreen(
            artistId = key.artistId,
            onAssetClick = openProductDetail,
        )
    }
}
