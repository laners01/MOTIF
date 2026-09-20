package com.ssafy.motif.feature.motif.impl.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.ssafy.motif.core.navigation.Navigator
import com.ssafy.motif.feature.motif.api.navigation.MotifNavKey
import com.ssafy.motif.feature.motif.api.navigation.PassPurchaseNavKey
import com.ssafy.motif.feature.motif.api.navigation.ProductDetailNavKey
import com.ssafy.motif.feature.motif.impl.MotifScreen
import com.ssafy.motif.feature.motif.impl.shop.detail.ProductDetailScreen
import com.ssafy.motif.feature.motif.impl.shop.pass.PassPurchaseScreen

fun EntryProviderScope<NavKey>.motifEntry(navigator: Navigator) {
    val openProductDetail: (artistId: Long, assetId: Long) -> Unit = { artistId, assetId ->
        navigator.navigate(
            ProductDetailNavKey(
                artistId = artistId,
                assetId = assetId,
            ),
        )
    }

    val openPassPurchase: (artistId: Long) -> Unit = { artistId ->
        navigator.navigate(
            PassPurchaseNavKey(artistId = artistId),
        )
    }

    entry<MotifNavKey> {
        MotifScreen(
            onBackClick = navigator::goBack,
            onProductClick = openProductDetail,
            onPassClick = openPassPurchase,
        )
    }

    // 모티프 탭의 sub stack 위에 쌓이는 화면들. 시스템 뒤로가기로 이전 화면으로 돌아간다.
    entry<ProductDetailNavKey> { key ->
        ProductDetailScreen(
            artistId = key.artistId,
            assetId = key.assetId,
            onPassClick = openPassPurchase,
        )
    }

    entry<PassPurchaseNavKey> { key ->
        PassPurchaseScreen(
            artistId = key.artistId,
            onAssetClick = openProductDetail,
        )
    }
}
