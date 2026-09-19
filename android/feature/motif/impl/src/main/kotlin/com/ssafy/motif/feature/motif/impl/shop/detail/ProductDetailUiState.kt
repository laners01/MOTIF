package com.ssafy.motif.feature.motif.impl.shop.detail

import androidx.compose.runtime.Immutable
import com.ssafy.motif.core.model.motif.Asset
import com.ssafy.motif.core.model.motif.AssetAvailability
import com.ssafy.motif.core.model.shop.ShopArtistDetail

internal sealed interface ProductDetailUiState {

    data object Loading : ProductDetailUiState

    @Immutable
    data class Success(
        val detail: ShopArtistDetail,
        /** 상단 미리보기와 선택 상품 카드, 구매 버튼의 대상. PASS는 별도 결제 화면으로 이동하므로 선택 대상은 항상 에셋이다. */
        val selectedAsset: Asset,
    ) : ProductDetailUiState {

        /** 선택된 에셋이 이미 구매된 것이면 구매 버튼을 비활성화한다. */
        val isSelectedAssetPurchased: Boolean
            get() = selectedAsset.availability == AssetAvailability.PURCHASED
    }

    data class Error(
        val exception: Throwable,
    ) : ProductDetailUiState
}
