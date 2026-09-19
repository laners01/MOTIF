package com.ssafy.motif.feature.motif.impl.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.ssafy.motif.core.navigation.Navigator
import com.ssafy.motif.feature.motif.api.navigation.MotifNavKey
import com.ssafy.motif.feature.motif.api.navigation.ProductDetailNavKey
import com.ssafy.motif.feature.motif.impl.MotifScreen
import com.ssafy.motif.feature.motif.impl.shop.detail.ProductDetailScreen

fun EntryProviderScope<NavKey>.motifEntry(navigator: Navigator) {
    entry<MotifNavKey> {
        MotifScreen(
            onBackClick = navigator::goBack,
            onProductClick = { artistId, assetId ->
                navigator.navigate(
                    ProductDetailNavKey(
                        artistId = artistId,
                        assetId = assetId,
                    ),
                )
            },
        )
    }

    // 모티프 탭의 sub stack 위에 쌓이는 상세. 시스템 뒤로가기로 샵으로 돌아간다.
    entry<ProductDetailNavKey> { key ->
        ProductDetailScreen(
            artistId = key.artistId,
            assetId = key.assetId,
            onPassClick = {
                // TODO PASS 결제 화면 이동 (별도 티켓)
            },
        )
    }
}
