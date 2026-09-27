package com.ssafy.motif.feature.motif.impl.shop.detail

import androidx.compose.runtime.Immutable
import com.ssafy.motif.core.model.motif.Asset
import com.ssafy.motif.core.model.motif.AssetAvailability
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
         * 개별 구매 또는 PASS로 이미 사용 가능한 경우
         * 다시 구매하지 않는다.
         */
        val isSelectedAssetPurchased: Boolean
            get() =
                selectedAsset.availability !=
                        AssetAvailability.LOCKED
    }

    data class Error(
        val exception: Throwable,
    ) : ProductDetailUiState
}