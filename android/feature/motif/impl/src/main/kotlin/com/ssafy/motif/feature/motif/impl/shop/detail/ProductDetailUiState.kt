package com.ssafy.motif.feature.motif.impl.shop.detail

import androidx.compose.runtime.Immutable
import com.ssafy.motif.core.model.motif.Asset
import com.ssafy.motif.core.model.shop.ShopArtistDetail

internal sealed interface ProductDetailUiState {

    data object Loading :
        ProductDetailUiState

    @Immutable
    data class Success(
        val detail: ShopArtistDetail,
        val selectedAsset: Asset,
    ) : ProductDetailUiState {

        /**
         * 개별 구매 또는 PASS로 현재 촬영 가능한지 여부.
         */
        val canUseSelectedAsset: Boolean
            get() =
                selectedAsset.isAvailable
    }

    data class Error(
        val exception: Throwable,
    ) : ProductDetailUiState
}
