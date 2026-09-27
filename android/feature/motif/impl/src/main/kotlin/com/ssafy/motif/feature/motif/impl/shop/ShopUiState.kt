package com.ssafy.motif.feature.motif.impl.shop

import androidx.compose.runtime.Immutable
import com.ssafy.motif.core.model.shop.PassBanner
import com.ssafy.motif.core.model.shop.ShopArtistSection

internal sealed interface ShopUiState {

    data object Loading : ShopUiState

    @Immutable
    data class Success(
        val banners: List<PassBanner>,
        val sections: List<ShopArtistSection>,
    ) : ShopUiState

    data class Error(
        val exception: Throwable,
    ) : ShopUiState
}