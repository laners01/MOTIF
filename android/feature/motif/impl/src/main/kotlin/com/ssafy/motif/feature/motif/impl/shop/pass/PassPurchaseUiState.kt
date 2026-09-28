package com.ssafy.motif.feature.motif.impl.shop.pass

import androidx.compose.runtime.Immutable
import com.ssafy.motif.core.model.shop.ShopArtistDetail

internal sealed interface PassPurchaseUiState {

    data object Loading : PassPurchaseUiState

    @Immutable
    data class Success(
        /** 배너·안내·에셋 목록·플랜은 상품 상세와 같은 데이터에서 온다. */
        val detail: ShopArtistDetail,
    ) : PassPurchaseUiState

    data class Error(
        val exception: Throwable,
    ) : PassPurchaseUiState
}
